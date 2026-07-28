package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import bd.dms.world.AffectedArea;
import bd.dms.world.Camp;
import bd.dms.world.CampResource;
import bd.dms.world.CampResourceRepository;
import bd.dms.world.Disaster;
import bd.dms.world.DisasterAdminService;
import bd.dms.world.GeometryHistory;
import bd.dms.world.GeometrySubjectType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Exercises {@link DisasterAdminService} directly — the sole writer of manually-registered
 * world-structure change. Covers every method plus the geometry-history retention scenario
 * called out explicitly in the ticket 13 plan: two sequential edits to the same disaster must
 * leave three history rows with the correct previous/new chain, not just the right count.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DisasterAdminServiceIntegrationTest {

    private static final String POLYGON_A = "{\"type\":\"Polygon\",\"coordinates\":[[[89.0,25.0]]]}";
    private static final String POLYGON_B = "{\"type\":\"Polygon\",\"coordinates\":[[[89.1,25.1]]]}";
    private static final String POLYGON_C = "{\"type\":\"Polygon\",\"coordinates\":[[[89.2,25.2]]]}";

    @Autowired
    private DisasterAdminService adminService;

    @Autowired
    private UserRepository users;

    @Autowired
    private CampResourceRepository campResources;

    private Long actorId() {
        return users.findByUsername("admin").orElseThrow().getId();
    }

    @Test
    void createDisasterPersistsAndWritesFirstGeometryHistoryRowWithNullPrevious() {
        Long actor = actorId();

        Disaster disaster = adminService.createDisaster(
                "test-flood-1", "FLOOD", "Test Flood", "টেস্ট বন্যা", POLYGON_A, actor);

        assertThat(disaster.getId()).isNotNull();
        assertThat(disaster.getStatus()).isEqualTo("ACTIVE");
        assertThat(disaster.getGeometry()).isEqualTo(POLYGON_A);

        List<GeometryHistory> history =
                adminService.getGeometryHistory(GeometrySubjectType.DISASTER, disaster.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getPreviousGeometry()).isNull();
        assertThat(history.get(0).getNewGeometry()).isEqualTo(POLYGON_A);
        assertThat(history.get(0).getActorUserId()).isEqualTo(actor);
    }

    @Test
    void updateDisasterGeometryAppendsHistoryButNameOnlyUpdateDoesNot() {
        Long actor = actorId();
        Disaster disaster = adminService.createDisaster(
                "test-flood-2", "FLOOD", "Test Flood 2", "টেস্ট বন্যা ২", POLYGON_A, actor);

        Disaster renamed = adminService.updateDisaster(disaster.getId(), "Renamed Flood", null, null, actor);
        assertThat(renamed.getNameEn()).isEqualTo("Renamed Flood");
        assertThat(renamed.getNameBn()).isEqualTo("টেস্ট বন্যা ২");
        assertThat(renamed.getGeometry()).isEqualTo(POLYGON_A);
        assertThat(adminService.getGeometryHistory(GeometrySubjectType.DISASTER, disaster.getId()))
                .hasSize(1);

        Disaster redrawn = adminService.updateDisaster(disaster.getId(), null, null, POLYGON_B, actor);
        assertThat(redrawn.getGeometry()).isEqualTo(POLYGON_B);
        List<GeometryHistory> history =
                adminService.getGeometryHistory(GeometrySubjectType.DISASTER, disaster.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(1).getPreviousGeometry()).isEqualTo(POLYGON_A);
        assertThat(history.get(1).getNewGeometry()).isEqualTo(POLYGON_B);
    }

    @Test
    void geometryHistoryRetainsExactChainAcrossTwoSequentialEdits() {
        Long actor = actorId();
        Disaster disaster = adminService.createDisaster(
                "test-flood-3", "FLOOD", "Test Flood 3", "টেস্ট বন্যা ৩", POLYGON_A, actor);

        adminService.updateDisaster(disaster.getId(), null, null, POLYGON_B, actor);
        adminService.updateDisaster(disaster.getId(), null, null, POLYGON_C, actor);

        List<GeometryHistory> history =
                adminService.getGeometryHistory(GeometrySubjectType.DISASTER, disaster.getId());

        assertThat(history).hasSize(3);

        assertThat(history.get(0).getPreviousGeometry()).isNull();
        assertThat(history.get(0).getNewGeometry()).isEqualTo(POLYGON_A);

        assertThat(history.get(1).getPreviousGeometry()).isEqualTo(POLYGON_A);
        assertThat(history.get(1).getNewGeometry()).isEqualTo(POLYGON_B);

        assertThat(history.get(2).getPreviousGeometry()).isEqualTo(POLYGON_B);
        assertThat(history.get(2).getNewGeometry()).isEqualTo(POLYGON_C);
    }

    @Test
    void updateDisasterThrowsOnUnknownId() {
        assertThatThrownBy(() -> adminService.updateDisaster(999_999L, "x", null, null, actorId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void closeDisasterSetsStatusToClosed() {
        Disaster disaster = adminService.createDisaster(
                "test-flood-4", "FLOOD", "Test Flood 4", "টেস্ট বন্যা ৪", POLYGON_A, actorId());

        Disaster closed = adminService.closeDisaster(disaster.getId(), actorId());

        assertThat(closed.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void closeDisasterThrowsOnUnknownId() {
        assertThatThrownBy(() -> adminService.closeDisaster(999_999L, actorId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createAffectedAreaPersistsAndWritesGeometryHistoryWithNullPrevious() {
        Long actor = actorId();
        Disaster disaster = adminService.createDisaster(
                "test-flood-5", "FLOOD", "Test Flood 5", "টেস্ট বন্যা ৫", POLYGON_A, actor);

        AffectedArea area = adminService.createAffectedArea(
                disaster.getId(), "Char Belt", "চর বেল্ট", POLYGON_B, actor);

        assertThat(area.getId()).isNotNull();
        assertThat(area.getDisasterId()).isEqualTo(disaster.getId());
        assertThat(area.getGeometry()).isEqualTo(POLYGON_B);

        List<GeometryHistory> history =
                adminService.getGeometryHistory(GeometrySubjectType.AFFECTED_AREA, area.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getPreviousGeometry()).isNull();
        assertThat(history.get(0).getNewGeometry()).isEqualTo(POLYGON_B);
    }

    @Test
    void createAffectedAreaThrowsOnUnknownDisaster() {
        assertThatThrownBy(() -> adminService.createAffectedArea(
                        999_999L, "x", "y", POLYGON_A, actorId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createCampPersistsAndBootstrapsThreeResourceRowsMatchingV6Formula() {
        Long actor = actorId();
        Disaster disaster = adminService.createDisaster(
                "test-flood-6", "FLOOD", "Test Flood 6", "টেস্ট বন্যা ৬", POLYGON_A, actor);

        Camp camp = adminService.createCamp(
                disaster.getId(), "test-flood-6-camp-1", "New Camp", "নতুন ক্যাম্প",
                24.9, 89.3, 500, 200, actor);

        assertThat(camp.getId()).isNotNull();
        assertThat(camp.getStatus()).isEqualTo("OPEN");
        assertThat(camp.getCapacity()).isEqualTo(500);
        assertThat(camp.getPopulation()).isEqualTo(200);

        List<CampResource> rows = campResources.findByCampId(camp.getId());
        assertThat(rows).hasSize(3);

        assertThat(rows).anySatisfy(r -> {
            assertThat(r.getResourceType()).isEqualTo("WATER");
            assertThat(r.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(600));
            assertThat(r.getUnit()).isEqualTo("liters/day");
        });
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.getResourceType()).isEqualTo("FOOD");
            assertThat(r.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(400));
            assertThat(r.getUnit()).isEqualTo("meal packs");
        });
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.getResourceType()).isEqualTo("MEDICAL");
            assertThat(r.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(200));
            assertThat(r.getUnit()).isEqualTo("aid kits");
        });
    }

    @Test
    void createCampThrowsOnUnknownDisaster() {
        assertThatThrownBy(() -> adminService.createCamp(
                        999_999L, "x", "y", "z", 0, 0, 10, 5, actorId()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
