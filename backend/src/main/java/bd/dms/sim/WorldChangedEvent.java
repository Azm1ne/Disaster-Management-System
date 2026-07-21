package bd.dms.sim;

/**
 * Published by {@link SimulationEngine} whenever the world or the clock changes. The realtime
 * layer listens for it and pushes the new state to subscribers — keeping the engine itself free
 * of any transport dependency, which is what lets it stay the clean sole writer of simulated
 * change.
 *
 * <p>{@code worldChanged} distinguishes a tick or reset (camp rows were rewritten, so the world
 * must be re-read and pushed) from a clock-only change such as pause, resume, or speed.
 */
public record WorldChangedEvent(SimulationClock clock, boolean worldChanged) {}
