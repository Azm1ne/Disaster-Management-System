package bd.dms.sim;

import java.util.Map;

/**
 * The whole simulated world at one tick: every camp's {@link CampState}, keyed by camp code.
 * Deterministically produced by {@link Scenario#stateAt(long)} — the same tick always yields an
 * equal {@code ScenarioState}, which is what makes the simulation reproducible tick-for-tick.
 */
public record ScenarioState(Map<String, CampState> camps) {

    public CampState camp(String code) {
        return camps.get(code);
    }
}
