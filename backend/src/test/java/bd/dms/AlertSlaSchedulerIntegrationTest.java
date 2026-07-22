package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bd.dms.alert.Alert;
import bd.dms.alert.AlertService;
import bd.dms.alert.AlertStatus;
import bd.dms.alert.AlertType;
import bd.dms.sim.SimulationEngine;
import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The SLA sweep is a leaf {@code @Scheduled} component like {@code SimulationScheduler}, so it
 * runs on the real Spring scheduler here rather than being driven by hand — the thing under test
 * is that the sweep actually fires and finds stale alerts, not the timing math (that lives in
 * {@code AlertSla} and is unit-tested there).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AlertSlaSchedulerIntegrationTest {

    @Autowired
    private AlertService alerts;

    @Autowired
    private UserRepository users;

    @Autowired
    private CampRepository camps;

    @Autowired
    private SimulationEngine engine;

    @Test
    void aStaleMedicalEmergencyIsAutoEscalatedOnceItsSlaTickHasPassed() {
        AppUser coordinator = users.findByUsername("coordinator").orElseThrow();
        Camp camp = camps.findByCode("jam-kurigram-sadar").orElseThrow();
        Alert alert = alerts.raise(coordinator, AlertType.MEDICAL_EMERGENCY, camp.getId(), "Casualty");

        for (int i = 0; i < 3; i++) {
            engine.advance();
        }

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(alerts.visibleDetail(coordinator, alert.getId()))
                        .get()
                        .extracting(Alert::getStatus)
                        .isEqualTo(AlertStatus.ESCALATED));
    }
}
