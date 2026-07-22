package bd.dms.sim;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The simulated state of one camp at a single tick: its headline population and status plus
 * its resource quantities keyed by type ({@code WATER} / {@code FOOD} / {@code MEDICAL}). This
 * is what {@link Scenario#stateAt(long)} produces and what {@code SimulationEngine} writes into
 * the real {@code camps} / {@code camp_resources} rows. Pure data — no persistence concern.
 */
public record CampState(String code, int population, String status, Map<String, BigDecimal> resources) {}
