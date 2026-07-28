package bd.dms.world;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sole writer of manually-registered world-structure change — creating/editing disasters,
 * affected areas, and camps outside the simulation (mirrors {@code SimulationEngine} being the
 * sole writer of simulated camp/resource state). Both the direct admin controller (ticket 13
 * task 3) and proposal-approval (task 4) call into this service so the two paths can never
 * diverge in behaviour.
 *
 * <p>An admin-created camp is not picked up by {@code SimulationEngine}, which ticks a fixed,
 * hardcoded camp-code list rather than every row in the table — it will show up correctly in
 * every repository-driven read (map, allocation, forecast) but will stay static rather than
 * surging. This is a deliberate, documented limitation of this slice, not an oversight.
 */
@Service
@Transactional
public class DisasterAdminService {

    /** Per-person daily resource seed, matching V6__seed_camp_resources.sql exactly. */
    private static final BigDecimal WATER_PER_PERSON = BigDecimal.valueOf(3);
    private static final BigDecimal FOOD_PER_PERSON = BigDecimal.valueOf(2);
    private static final BigDecimal MEDICAL_PER_PERSON = BigDecimal.valueOf(1);

    private final DisasterRepository disasters;
    private final AffectedAreaRepository areas;
    private final CampRepository camps;
    private final CampResourceRepository resources;
    private final GeometryHistoryRepository geometryHistory;

    public DisasterAdminService(
            DisasterRepository disasters,
            AffectedAreaRepository areas,
            CampRepository camps,
            CampResourceRepository resources,
            GeometryHistoryRepository geometryHistory) {
        this.disasters = disasters;
        this.areas = areas;
        this.camps = camps;
        this.resources = resources;
        this.geometryHistory = geometryHistory;
    }

    /** Registers a new disaster, active from creation, with its admin-drawn boundary polygon. */
    public Disaster createDisaster(
            String code, String type, String nameEn, String nameBn, String geometry, Long actorUserId) {
        Disaster disaster = disasters.save(new Disaster(code, type, "ACTIVE", nameEn, nameBn, geometry));
        geometryHistory.save(new GeometryHistory(
                GeometrySubjectType.DISASTER, disaster.getId(), null, geometry, actorUserId));
        return disaster;
    }

    /**
     * Updates a disaster's name and/or boundary. Every parameter is optional: a null leaves the
     * existing value untouched. A geometry change appends a {@link GeometryHistory} row carrying
     * the pre-update value as {@code previousGeometry}; a name-only update writes no history row.
     */
    public Disaster updateDisaster(Long disasterId, String nameEn, String nameBn, String geometry, Long actorUserId) {
        Disaster disaster = disasters.findById(disasterId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown disaster: " + disasterId));

        if (nameEn != null) {
            disaster.setNameEn(nameEn);
        }
        if (nameBn != null) {
            disaster.setNameBn(nameBn);
        }
        if (geometry != null) {
            String previousGeometry = disaster.getGeometry();
            disaster.setGeometry(geometry);
            geometryHistory.save(new GeometryHistory(
                    GeometrySubjectType.DISASTER, disaster.getId(), previousGeometry, geometry, actorUserId));
        }
        return disasters.save(disaster);
    }

    /**
     * Closes a disaster. {@code status} is display-only today: no engine branches on it.
     * {@code actorUserId} isn't persisted here (no audit trail exists for status changes yet)
     * but is kept in the signature for parity with the other mutating methods.
     */
    public Disaster closeDisaster(Long disasterId, Long actorUserId) {
        Disaster disaster = disasters.findById(disasterId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown disaster: " + disasterId));
        disaster.setStatus("CLOSED");
        return disasters.save(disaster);
    }

    /** Registers a new affected area (polygon) under an existing disaster. */
    public AffectedArea createAffectedArea(
            Long disasterId, String nameEn, String nameBn, String geometry, Long actorUserId) {
        disasters.findById(disasterId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown disaster: " + disasterId));

        AffectedArea area = areas.save(new AffectedArea(disasterId, nameEn, nameBn, geometry));
        geometryHistory.save(new GeometryHistory(
                GeometrySubjectType.AFFECTED_AREA, area.getId(), null, geometry, actorUserId));
        return area;
    }

    /**
     * Registers a new camp (point location, no polygon — no geometry history row) and bootstraps
     * its {@link CampResource} rows so forecast/allocation code sees real data from the start,
     * using the same per-person multipliers as V6__seed_camp_resources.sql.
     */
    public Camp createCamp(
            Long disasterId,
            String code,
            String nameEn,
            String nameBn,
            double lat,
            double lng,
            int capacity,
            int initialPopulation,
            Long actorUserId) {
        disasters.findById(disasterId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown disaster: " + disasterId));

        Camp camp = camps.save(new Camp(
                disasterId, code, nameEn, nameBn, lat, lng, capacity, initialPopulation, "OPEN"));

        BigDecimal population = BigDecimal.valueOf(initialPopulation);
        resources.save(new CampResource(
                camp.getId(), "WATER", WATER_PER_PERSON.multiply(population), "liters/day"));
        resources.save(new CampResource(
                camp.getId(), "FOOD", FOOD_PER_PERSON.multiply(population), "meal packs"));
        resources.save(new CampResource(
                camp.getId(), "MEDICAL", MEDICAL_PER_PERSON.multiply(population), "aid kits"));

        return camp;
    }

    /** A subject's geometry history, oldest first — the audit trail Task 3's controller exposes. */
    @Transactional(readOnly = true)
    public List<GeometryHistory> getGeometryHistory(GeometrySubjectType subjectType, Long subjectId) {
        return geometryHistory.findBySubjectTypeAndSubjectIdOrderByCreatedAtAsc(subjectType, subjectId);
    }
}
