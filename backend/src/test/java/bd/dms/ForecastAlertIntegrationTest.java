package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;

import bd.dms.alert.Alert;
import bd.dms.alert.AlertRepository;
import bd.dms.alert.AlertType;
import bd.dms.forecast.CampResourceObservation;
import bd.dms.forecast.CampResourceObservationRepository;
import bd.dms.sim.SimulationClock;
import bd.dms.sim.WorldChangedEvent;
import bd.dms.world.CampRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Proves {@code ForecastAlertListener} is really wired to {@link WorldChangedEvent}: publishing
 * the event after a camp's resource history shows a steep depletion trend raises a
 * {@code RESOURCE_SHORTAGE} alert, and re-publishing while that alert is still open does not
 * raise a second one (dedupe is {@code AlertService}'s, exercised here through the listener).
 *
 * <p>The scripted {@code Scenario} run is deliberately not used to drive this: its coverage floor
 * (0.55 for WATER/FOOD, 0.85 for MEDICAL) means no camp/resource/tick in the real 60-tick script
 * ever drives {@code ticksRemainingWorstCase} at or below the 6-tick threshold — the closest any
 * combination gets across the whole run is 18 (jam-nageshwari WATER at tick 59). So this test
 * seeds a synthetic, steeply-depleting observation history directly and publishes the event by
 * hand, which still exercises the real listener bean registered on the real event.
 */
@SpringBootTest
class ForecastAlertIntegrationTest {

    private static final String CAMP_CODE = "jam-nageshwari"; // unused by any other test in this suite
    private static final String RESOURCE_TYPE = "WATER";

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private AlertRepository alerts;

    @Autowired
    private CampRepository camps;

    @Autowired
    private CampResourceObservationRepository observations;

    @Test
    void aSteepDepletionTrendRaisesAResourceShortageAlert() {
        Long campId = camps.findByCode(CAMP_CODE).orElseThrow().getId();
        seedSteepDepletion(campId);

        events.publishEvent(new WorldChangedEvent(clockAt(20), true));

        assertThat(shortageAlertsFor(campId)).hasSize(1);
    }

    @Test
    void theSameShortageIsNotRaisedTwiceWhileStillOpen() {
        Long campId = camps.findByCode(CAMP_CODE).orElseThrow().getId();
        seedSteepDepletion(campId);

        events.publishEvent(new WorldChangedEvent(clockAt(20), true));
        long firstCount = shortageAlertsFor(campId).size();

        events.publishEvent(new WorldChangedEvent(clockAt(21), true)); // condition still holds

        assertThat(shortageAlertsFor(campId)).hasSize((int) firstCount);
    }

    private List<Alert> shortageAlertsFor(Long campId) {
        return alerts.findAll().stream()
                .filter(a -> a.getType() == AlertType.RESOURCE_SHORTAGE
                        && a.getCampId().equals(campId)
                        && RESOURCE_TYPE.equals(a.getResourceType()))
                .toList();
    }

    /** Ticks 14..20, quantity falling 1000 -> 100 (150/tick): a rate far steeper than any stock
     * on hand, so ticksRemainingWorstCase is close to 0 regardless of the confidence band. */
    private void seedSteepDepletion(Long campId) {
        for (long tick = 14; tick <= 20; tick++) {
            BigDecimal quantity = BigDecimal.valueOf(1000 - (tick - 14) * 150);
            observations.save(new CampResourceObservation(campId, RESOURCE_TYPE, quantity, tick));
        }
    }

    private SimulationClock clockAt(long tick) {
        return new SimulationClock(tick, Instant.now(), "SURGE", true, 1.0, 60);
    }
}
