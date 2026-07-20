package bd.dms.sim;

/**
 * Published by {@link SimulationEngine} whenever the world or the clock changes (a tick, or a
 * pause/resume/speed/reset). The realtime layer listens for it and pushes the new world and
 * clock to subscribers — keeping the engine itself free of any transport dependency, which is
 * what lets it stay the clean sole writer of simulated change.
 */
public record WorldChangedEvent(SimulationClock clock) {}
