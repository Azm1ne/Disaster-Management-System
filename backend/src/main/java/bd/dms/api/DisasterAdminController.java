package bd.dms.api;

import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import bd.dms.world.AffectedArea;
import bd.dms.world.Camp;
import bd.dms.world.Disaster;
import bd.dms.world.DisasterAdminService;
import bd.dms.world.GeometryHistory;
import bd.dms.world.GeometrySubjectType;
import bd.dms.world.dto.AffectedAreaAdminView;
import bd.dms.world.dto.CampAdminView;
import bd.dms.world.dto.DisasterAdminView;
import bd.dms.world.dto.GeometryHistoryView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The direct-write admin surface for manually registering world structure — disasters,
 * affected areas, camps — and reading the geometry-edit trail. Every mutation delegates to
 * {@link DisasterAdminService}, the sole writer of manually-registered change, so this
 * controller only resolves the caller and shapes request/response bodies.
 *
 * <p>{@code /admin/**} is already gated to {@code hasRole("ADMIN")} by {@code SecurityConfig}
 * (a wildcard covering every path under this controller), so no new security rule is added
 * here. An unknown disaster/affected-area id surfaces as a 400 via {@code ApiExceptionHandler}'s
 * existing {@code IllegalArgumentException} mapping, matching every other service in this
 * codebase (e.g. {@code AllocationService}, {@code AlertService}).
 */
@RestController
@RequestMapping("/admin")
public class DisasterAdminController {

    public record CreateDisasterRequest(
            @NotBlank String code, @NotBlank String type, @NotBlank String nameEn,
            @NotBlank String nameBn, @NotBlank String geometry) {}

    /** Every field optional: null leaves that value unchanged (see {@code DisasterAdminService.updateDisaster}). */
    public record UpdateDisasterRequest(String nameEn, String nameBn, String geometry) {}

    public record CreateAffectedAreaRequest(
            @NotBlank String nameEn, @NotBlank String nameBn, @NotBlank String geometry) {}

    public record CreateCampRequest(
            @NotBlank String code,
            @NotBlank String nameEn,
            @NotBlank String nameBn,
            double lat,
            double lng,
            @PositiveOrZero int capacity,
            @PositiveOrZero int initialPopulation) {}

    private final DisasterAdminService adminService;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    public DisasterAdminController(DisasterAdminService adminService, UserRepository users, ObjectMapper objectMapper) {
        this.adminService = adminService;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/disasters")
    public DisasterAdminView createDisaster(@Valid @RequestBody CreateDisasterRequest request, Authentication authentication) {
        Disaster disaster = adminService.createDisaster(
                request.code(), request.type(), request.nameEn(), request.nameBn(), request.geometry(),
                actor(authentication).getId());
        return toView(disaster);
    }

    @PutMapping("/disasters/{id}")
    public DisasterAdminView updateDisaster(
            @PathVariable Long id, @Valid @RequestBody UpdateDisasterRequest request, Authentication authentication) {
        Disaster disaster = adminService.updateDisaster(
                id, request.nameEn(), request.nameBn(), request.geometry(), actor(authentication).getId());
        return toView(disaster);
    }

    @PostMapping("/disasters/{id}/close")
    public DisasterAdminView closeDisaster(@PathVariable Long id, Authentication authentication) {
        Disaster disaster = adminService.closeDisaster(id, actor(authentication).getId());
        return toView(disaster);
    }

    @PostMapping("/disasters/{id}/affected-areas")
    public AffectedAreaAdminView createAffectedArea(
            @PathVariable Long id, @Valid @RequestBody CreateAffectedAreaRequest request, Authentication authentication) {
        AffectedArea area = adminService.createAffectedArea(
                id, request.nameEn(), request.nameBn(), request.geometry(), actor(authentication).getId());
        return toView(area);
    }

    @PostMapping("/disasters/{id}/camps")
    public CampAdminView createCamp(
            @PathVariable Long id, @Valid @RequestBody CreateCampRequest request, Authentication authentication) {
        Camp camp = adminService.createCamp(
                id, request.code(), request.nameEn(), request.nameBn(), request.lat(), request.lng(),
                request.capacity(), request.initialPopulation(), actor(authentication).getId());
        return toView(camp);
    }

    @GetMapping("/disasters/{id}/geometry-history")
    public List<GeometryHistoryView> disasterGeometryHistory(@PathVariable Long id) {
        return adminService.getGeometryHistory(GeometrySubjectType.DISASTER, id).stream()
                .map(this::toView)
                .toList();
    }

    @GetMapping("/affected-areas/{id}/geometry-history")
    public List<GeometryHistoryView> affectedAreaGeometryHistory(@PathVariable Long id) {
        return adminService.getGeometryHistory(GeometrySubjectType.AFFECTED_AREA, id).stream()
                .map(this::toView)
                .toList();
    }

    private AppUser actor(Authentication authentication) {
        return users.findByUsername(authentication.getName()).orElseThrow();
    }

    private DisasterAdminView toView(Disaster disaster) {
        return new DisasterAdminView(
                disaster.getId(), disaster.getCode(), disaster.getType(), disaster.getStatus(),
                disaster.getNameEn(), disaster.getNameBn(), geometryOf(disaster.getGeometry()));
    }

    private AffectedAreaAdminView toView(AffectedArea area) {
        return new AffectedAreaAdminView(
                area.getId(), area.getDisasterId(), area.getNameEn(), area.getNameBn(), geometryOf(area.getGeometry()));
    }

    private CampAdminView toView(Camp camp) {
        return new CampAdminView(
                camp.getId(), camp.getDisasterId(), camp.getCode(), camp.getNameEn(), camp.getNameBn(),
                camp.getLat(), camp.getLng(), camp.getCapacity(), camp.getPopulation(), camp.getStatus());
    }

    private GeometryHistoryView toView(GeometryHistory history) {
        return new GeometryHistoryView(
                history.getId(), history.getSubjectId(), geometryOf(history.getPreviousGeometry()),
                geometryOf(history.getNewGeometry()), history.getActorUserId(), history.getCreatedAt());
    }

    /** Parses admin-authored geometry text to a GeoJSON object; null in (unset boundary, or a
     * history row's first-write {@code previousGeometry}) stays null out. */
    private JsonNode geometryOf(String geometry) {
        if (geometry == null) {
            return null;
        }
        try {
            return objectMapper.readTree(geometry);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid GeoJSON: " + geometry, e);
        }
    }
}
