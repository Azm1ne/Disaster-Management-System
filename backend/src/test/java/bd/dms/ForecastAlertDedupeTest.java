package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;

import bd.dms.alert.Alert;
import bd.dms.alert.AlertService;
import bd.dms.alert.AlertStatus;
import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ForecastAlertDedupeTest {

    @Autowired
    private AlertService alerts;

    @Autowired
    private CampRepository camps;

    @Autowired
    private UserRepository users;

    @Test
    void raisingTwiceForTheSameCampAndResourceReturnsTheSameOpenAlert() {
        Long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();

        Alert first = alerts.raiseFromForecast(campId, "WATER", "Projected WATER exhaustion");
        Alert second = alerts.raiseFromForecast(campId, "WATER", "Projected WATER exhaustion");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(first.getResourceType()).isEqualTo("WATER");
        assertThat(first.getRaisedByUserId()).isNull();
    }

    @Test
    void differentResourceOnTheSameCampRaisesAnIndependentAlert() {
        Long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();

        Alert water = alerts.raiseFromForecast(campId, "WATER", "Projected WATER exhaustion");
        Alert food = alerts.raiseFromForecast(campId, "FOOD", "Projected FOOD exhaustion");

        assertThat(water.getId()).isNotEqualTo(food.getId());
    }

    @Test
    void aNewAlertCanBeRaisedOnceTheOpenOneIsClosed() {
        Long campId = camps.findByCode("jam-chilmari").orElseThrow().getId();
        AppUser coordinator = users.findByUsername("coordinator").orElseThrow();

        Alert first = alerts.raiseFromForecast(campId, "MEDICAL", "Projected MEDICAL exhaustion");
        alerts.transition(coordinator, first.getId(), AlertStatus.ACKNOWLEDGED, null);
        alerts.transition(coordinator, first.getId(), AlertStatus.IN_PROGRESS, null);
        alerts.transition(coordinator, first.getId(), AlertStatus.RESOLVED, null);

        Alert second = alerts.raiseFromForecast(campId, "MEDICAL", "Projected MEDICAL exhaustion again");

        assertThat(second.getId()).isNotEqualTo(first.getId());
    }
}
