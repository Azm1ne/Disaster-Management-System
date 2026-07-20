package bd.dms.world;

import bd.dms.world.dto.AffectedAreaView;
import bd.dms.world.dto.CampDetail;
import bd.dms.world.dto.CampSummary;
import bd.dms.world.dto.DisasterView;
import bd.dms.world.dto.LocatorCamp;
import bd.dms.world.dto.ResourceView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the seeded world and shapes it into the views the API returns. Two audiences are
 * served from the same data with deliberately different shapes: authenticated callers get the
 * full operational picture; the public locator gets only name, location, and status.
 */
@Service
@Transactional(readOnly = true)
public class WorldService {

    private final DisasterRepository disasters;
    private final AffectedAreaRepository areas;
    private final CampRepository camps;
    private final CampResourceRepository resources;
    private final ObjectMapper objectMapper;

    public WorldService(
            DisasterRepository disasters,
            AffectedAreaRepository areas,
            CampRepository camps,
            CampResourceRepository resources,
            ObjectMapper objectMapper) {
        this.disasters = disasters;
        this.areas = areas;
        this.camps = camps;
        this.resources = resources;
        this.objectMapper = objectMapper;
    }

    /** Every disaster world, each with its areas to draw and its camps' headline state. */
    public List<DisasterView> allDisasters() {
        Map<Long, List<AffectedArea>> areasByDisaster =
                areas.findAll().stream().collect(Collectors.groupingBy(AffectedArea::getDisasterId));
        Map<Long, List<Camp>> campsByDisaster =
                camps.findAll().stream().collect(Collectors.groupingBy(Camp::getDisasterId));

        return disasters.findAll().stream()
                .map(disaster -> toDisasterView(
                        disaster,
                        areasByDisaster.getOrDefault(disaster.getId(), List.of()),
                        campsByDisaster.getOrDefault(disaster.getId(), List.of())))
                .toList();
    }

    /** One camp's full detail, or empty if no camp has that id. */
    public Optional<CampDetail> campDetail(Long campId) {
        return camps.findById(campId).map(this::toCampDetail);
    }

    /** The public locator's whitelisted view of every camp across all disasters. */
    public List<LocatorCamp> publicLocator() {
        return camps.findAllByOrderByNameEnAsc().stream()
                .map(camp -> new LocatorCamp(
                        camp.getId(), camp.getNameEn(), camp.getNameBn(),
                        camp.getLat(), camp.getLng(), camp.getStatus()))
                .toList();
    }

    private DisasterView toDisasterView(Disaster disaster, List<AffectedArea> disasterAreas, List<Camp> disasterCamps) {
        List<AffectedAreaView> areaViews = disasterAreas.stream()
                .map(area -> new AffectedAreaView(
                        area.getId(), area.getNameEn(), area.getNameBn(), geometryOf(area)))
                .toList();
        List<CampSummary> campViews = disasterCamps.stream().map(this::toCampSummary).toList();
        return new DisasterView(
                disaster.getId(), disaster.getCode(), disaster.getType(), disaster.getStatus(),
                disaster.getNameEn(), disaster.getNameBn(), areaViews, campViews);
    }

    private CampSummary toCampSummary(Camp camp) {
        return new CampSummary(
                camp.getId(), camp.getCode(), camp.getNameEn(), camp.getNameBn(),
                camp.getLat(), camp.getLng(), camp.getCapacity(), camp.getPopulation(), camp.getStatus());
    }

    private CampDetail toCampDetail(Camp camp) {
        CampDetail.DisasterRef disasterRef = disasters.findById(camp.getDisasterId())
                .map(d -> new CampDetail.DisasterRef(d.getId(), d.getNameEn(), d.getNameBn()))
                .orElse(null);
        List<ResourceView> resourceViews = resources.findByCampId(camp.getId()).stream()
                .map(r -> new ResourceView(r.getResourceType(), r.getQuantity(), r.getUnit()))
                .toList();
        return new CampDetail(
                camp.getId(), camp.getCode(), camp.getNameEn(), camp.getNameBn(),
                camp.getLat(), camp.getLng(), camp.getCapacity(), camp.getPopulation(), camp.getStatus(),
                disasterRef, resourceViews);
    }

    private JsonNode geometryOf(AffectedArea area) {
        try {
            return objectMapper.readTree(area.getGeometry());
        } catch (JsonProcessingException e) {
            // Seed geometry is authored as valid GeoJSON; a parse failure is a seed bug, not a
            // runtime condition to recover from.
            throw new IllegalStateException("Invalid GeoJSON for affected area " + area.getId(), e);
        }
    }
}
