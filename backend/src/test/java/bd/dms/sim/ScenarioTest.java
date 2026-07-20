package bd.dms.sim;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The scenario is the pure spine of the simulation, so its guarantees are asserted directly,
 * with no Spring context or database: reproducibility tick-for-tick, the scripted phases
 * actually happening (a camp opening mid-run, populations rising then receding), and the
 * Patuakhali cyclone staying quiescent.
 */
class ScenarioTest {

    @Test
    void stateAtIsReproducibleTickForTick() {
        for (long tick : new long[] {0, 20, 35, 60}) {
            assertThat(Scenario.stateAt(tick))
                    .as("tick %d", tick)
                    .isEqualTo(Scenario.stateAt(tick));
        }
    }

    @Test
    void overflowCampOpensMidRun() {
        CampState atStart = Scenario.stateAt(0).camp("jam-char-relief");
        assertThat(atStart.status()).isEqualTo("CLOSED");
        assertThat(atStart.population()).isZero();

        CampState afterOpen = Scenario.stateAt(21).camp("jam-char-relief");
        assertThat(afterOpen.status()).isEqualTo("OPEN");
        assertThat(afterOpen.population()).isPositive();
    }

    @Test
    void riverineCampSurgesThenRecovers() {
        int base = Scenario.stateAt(0).camp("jam-nageshwari").population();
        int peak = Scenario.stateAt(30).camp("jam-nageshwari").population();
        int recovered = Scenario.stateAt(60).camp("jam-nageshwari").population();

        assertThat(peak).as("population surges above baseline").isGreaterThan(base);
        assertThat(recovered).as("recovery recedes below the peak").isLessThan(peak);
        assertThat(recovered).as("but stays above the pre-flood baseline").isGreaterThan(base);
    }

    @Test
    void waterDepletesIntoShortageThenConvoyReplenishes() {
        java.math.BigDecimal atOpen = Scenario.stateAt(0).camp("jam-kurigram-sadar").resources().get("WATER");
        java.math.BigDecimal duringSurge = Scenario.stateAt(20).camp("jam-kurigram-sadar").resources().get("WATER");
        java.math.BigDecimal afterConvoy = Scenario.stateAt(40).camp("jam-kurigram-sadar").resources().get("WATER");

        // Per-capita coverage falls in the surge (stock per person drops) then the convoy lifts it.
        double perCapOpen = atOpen.doubleValue() / Scenario.stateAt(0).camp("jam-kurigram-sadar").population();
        double perCapSurge = duringSurge.doubleValue() / Scenario.stateAt(20).camp("jam-kurigram-sadar").population();
        double perCapConvoy = afterConvoy.doubleValue() / Scenario.stateAt(40).camp("jam-kurigram-sadar").population();
        assertThat(perCapSurge).as("shortage during surge").isLessThan(perCapOpen);
        assertThat(perCapConvoy).as("convoy replenishes").isGreaterThan(perCapSurge);
    }

    @Test
    void patuakhaliCycloneIsQuiescent() {
        for (long tick : new long[] {0, 20, 35, 60}) {
            assertThat(Scenario.stateAt(tick).camp("pat-sadar"))
                    .as("tick %d", tick)
                    .isEqualTo(Scenario.stateAt(0).camp("pat-sadar"));
        }
    }

    @Test
    void phasesFollowTheScript() {
        assertThat(Scenario.phaseAt(0)).isEqualTo("SURGE");
        assertThat(Scenario.phaseAt(20)).isEqualTo("NEW_CAMP");
        assertThat(Scenario.phaseAt(35)).isEqualTo("RELIEF_CONVOY");
        assertThat(Scenario.phaseAt(45)).isEqualTo("RECOVERY");
        assertThat(Scenario.phaseAt(60)).isEqualTo("RECOVERY");
    }
}
