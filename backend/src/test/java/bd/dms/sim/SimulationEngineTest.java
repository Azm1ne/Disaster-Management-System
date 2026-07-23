package bd.dms.sim;

import static org.assertj.core.api.Assertions.assertThat;

import bd.dms.forecast.CampResourceObservation;
import bd.dms.forecast.CampResourceObservationRepository;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The engine seam: proves that driving the engine actually writes the scripted world into the
 * real camp rows, that engine output matches the pure {@link Scenario} tick-for-tick, that a
 * changed speed leaves the state sequence untouched, and that reset returns to baseline. Runs on
 * the H2 test database with the full V1–V6 seed applied.
 */
@SpringBootTest
class SimulationEngineTest {

    @Autowired
    private SimulationEngine engine;

    @Autowired
    private CampRepository camps;

    @Autowired
    private CampResourceObservationRepository observations;

    private Map<String, Camp> campsByCode() {
        return camps.findAll().stream().collect(Collectors.toMap(Camp::getCode, Function.identity()));
    }

    @Test
    void resetReturnsWorldToBaseline() {
        engine.advance();
        engine.advance();
        engine.reset();

        Map<String, Camp> world = campsByCode();
        assertThat(world.get("jam-kurigram-sadar").getPopulation()).isEqualTo(1080);
        assertThat(world.get("jam-kurigram-sadar").getStatus()).isEqualTo("OPEN");
        assertThat(world.get("jam-char-relief").getPopulation()).isZero();
        assertThat(world.get("jam-char-relief").getStatus()).isEqualTo("CLOSED");
        assertThat(engine.currentTick()).isZero();
        assertThat(engine.isRunning()).isFalse();
    }

    @Test
    void advanceWritesScenarioStateToTheDatabase() {
        engine.reset();
        for (int i = 0; i < 20; i++) {
            engine.advance();
        }

        Camp charRelief = campsByCode().get("jam-char-relief");
        // The overflow camp opens at tick 20 — proof the scripted mid-run event reached the DB.
        assertThat(charRelief.getStatus()).isEqualTo("OPEN");
        assertThat(campsByCode().get("jam-kurigram-sadar").getPopulation())
                .isEqualTo(Scenario.stateAt(20).camp("jam-kurigram-sadar").population());
    }

    @Test
    void engineSequenceMatchesThePureFunctionTickForTick() {
        engine.reset();
        for (long tick = 1; tick <= 5; tick++) {
            engine.advance();
            int expected = Scenario.stateAt(tick).camp("jam-nageshwari").population();
            assertThat(campsByCode().get("jam-nageshwari").getPopulation())
                    .as("tick %d", tick)
                    .isEqualTo(expected);
        }
    }

    @Test
    void speedChangesCadenceNotTheStateSequence() {
        engine.reset();
        engine.setSpeed(4.0);
        for (int i = 0; i < 3; i++) {
            engine.advance();
        }
        // Speed never enters the state function; tick 3 looks identical at any speed.
        assertThat(campsByCode().get("jam-nageshwari").getPopulation())
                .isEqualTo(Scenario.stateAt(3).camp("jam-nageshwari").population());
        assertThat(engine.clock().speed()).isEqualTo(4.0);
    }

    @Test
    void pauseAndResumeAreReflectedInTheClock() {
        engine.resume();
        assertThat(engine.clock().running()).isTrue();
        engine.pause();
        assertThat(engine.clock().running()).isFalse();
    }

    @Test
    void advanceAppendsAResourceObservationForEveryCampResource() {
        engine.reset();
        engine.advance();

        Long kurigramCampId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();
        List<CampResourceObservation> waterHistory = observations
                .findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(kurigramCampId, "WATER", 0);
        // Shared H2 instance across test methods (no per-test rollback): assert this advance's
        // row is present rather than assuming it's the last, since other methods in this class
        // also append to the same append-only table and JUnit doesn't guarantee method order.
        assertThat(waterHistory).isNotEmpty();
        assertThat(waterHistory).extracting(CampResourceObservation::getTick).contains(1L);
    }

    @Test
    void resetClearsThePriorObservationHistory() {
        engine.reset();
        engine.advance();
        engine.advance();

        engine.reset();

        // reset() re-applies tick 0 (which appends its own baseline observation rows), but must
        // not leave the ticks written by the advances before it lying around unboundedly.
        Long kurigramCampId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();
        List<CampResourceObservation> waterHistory = observations
                .findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(kurigramCampId, "WATER", 0);
        assertThat(waterHistory).extracting(CampResourceObservation::getTick).containsOnly(0L);
    }

    @Test
    void staleProneComboSkipsObservationsOnNonMultipleOfThreeTicks() {
        engine.reset();
        for (int i = 0; i < 2; i++) {
            engine.advance(); // ticks 1, 2 — neither is a multiple of 3
        }

        Long roumariCampId = camps.findByCode("jam-roumari").orElseThrow().getId();
        List<CampResourceObservation> history = observations
                .findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(roumariCampId, "WATER", 0);
        assertThat(history).extracting(CampResourceObservation::getTick).doesNotContain(1L, 2L);
    }
}
