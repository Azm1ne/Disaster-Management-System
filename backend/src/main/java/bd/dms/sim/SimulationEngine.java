package bd.dms.sim;

import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import bd.dms.world.CampResource;
import bd.dms.world.CampResourceRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The simulation clock and the <b>sole writer</b> of simulated world change. It holds the run
 * position ({@code tick}, {@code running}, {@code speed}) in memory — demo-appropriate, not
 * persisted across restart — and on each {@link #advance()} writes {@link Scenario#stateAt(long)}
 * into the real {@code camps} / {@code camp_resources} rows so the read API, the map, and the
 * realtime feed all reflect one source of truth.
 *
 * <p>Because the applied state is a pure function of {@code tick}, the four demo guarantees hold
 * by construction: reproducible tick-for-tick, pause halts mutation (the scheduler stops calling
 * {@link #advance()}), speed changes cadence not the state sequence, and {@link #reset()} returns
 * to the baseline. All state transitions are {@code synchronized} so the scheduler thread and
 * control-API request threads never interleave a mutation.
 */
@Service
public class SimulationEngine {

    private static final Set<Double> ALLOWED_SPEEDS = Set.of(0.5, 1.0, 2.0, 4.0, 8.0);

    private final CampRepository camps;
    private final CampResourceRepository resources;
    private final ApplicationEventPublisher events;

    private long tick = 0;
    private boolean running = false;
    private double speed = 1.0;

    public SimulationEngine(
            CampRepository camps, CampResourceRepository resources, ApplicationEventPublisher events) {
        this.camps = camps;
        this.resources = resources;
        this.events = events;
    }

    /** Advance one tick and write the new world, unless the run has reached its end (then hold). */
    @Transactional
    public synchronized SimulationClock advance() {
        if (tick >= Scenario.LENGTH) {
            return clock();
        }
        tick++;
        apply(Scenario.stateAt(tick));
        return publish(true);
    }

    /** Return to the scripted baseline (tick 0) and pause, so a demo restarts from a clean state. */
    @Transactional
    public synchronized SimulationClock reset() {
        tick = 0;
        running = false;
        apply(Scenario.stateAt(0));
        return publish(true);
    }

    public synchronized SimulationClock pause() {
        running = false;
        return publish(false);
    }

    public synchronized SimulationClock resume() {
        running = true;
        return publish(false);
    }

    /** Set the playback speed (cadence only — the state sequence is unchanged). */
    public synchronized SimulationClock setSpeed(double newSpeed) {
        if (!ALLOWED_SPEEDS.contains(newSpeed)) {
            throw new IllegalArgumentException("Unsupported speed: " + newSpeed);
        }
        speed = newSpeed;
        return publish(false);
    }

    public synchronized SimulationClock clock() {
        return new SimulationClock(
                tick, Scenario.simTimeAt(tick), Scenario.phaseAt(tick), running, speed, Scenario.LENGTH);
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized double speed() {
        return speed;
    }

    public synchronized long currentTick() {
        return tick;
    }

    /** Writes the given world state into the camp and resource rows (the only such write path). */
    private void apply(ScenarioState state) {
        Map<Long, String> codeByCampId = new HashMap<>();
        for (Camp camp : camps.findAll()) {
            CampState cs = state.camp(camp.getCode());
            if (cs == null) {
                continue;
            }
            codeByCampId.put(camp.getId(), camp.getCode());
            camp.setPopulation(cs.population());
            camp.setStatus(cs.status());
        }
        for (CampResource resource : resources.findAll()) {
            String code = codeByCampId.get(resource.getCampId());
            if (code == null) {
                continue;
            }
            BigDecimal quantity = state.camp(code).resources().get(resource.getResourceType());
            if (quantity != null) {
                resource.setQuantity(quantity);
            }
        }
    }

    private SimulationClock publish(boolean worldChanged) {
        SimulationClock clock = clock();
        events.publishEvent(new WorldChangedEvent(clock, worldChanged));
        return clock;
    }
}
