package bd.dms.sim;

import java.time.Instant;

/**
 * A snapshot of the simulation clock: where the scripted run is ({@code tick}, its
 * {@code simTime} and narrative {@code phase}) and how it is being driven ({@code running},
 * {@code speed}). Returned by the control API and pushed on {@code /topic/simulation} so every
 * screen's DEMO clock stays in step.
 */
public record SimulationClock(
        long tick, Instant simTime, String phase, boolean running, double speed, long scenarioLength) {}
