package bd.dms.sim;

import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the engine forward in wall-clock time. A fixed-rate beat checks whether enough real
 * time has passed for the current speed and, if the run is playing and not yet finished, advances
 * one tick. Speed only shortens the gap between ticks — it never changes which states are
 * produced — so "faster" means the same story told more quickly.
 *
 * <p>Disabled under {@code dms.simulation.auto-tick=false} (the tests, which drive the engine
 * explicitly). The timing decision is factored into {@link #beat()} with an injectable clock so it
 * can be unit-tested deterministically without the real scheduler.
 */
@Component
@ConditionalOnProperty(name = "dms.simulation.auto-tick", havingValue = "true", matchIfMissing = true)
public class SimulationScheduler {

    private final SimulationEngine engine;
    private final long baseTickMillis;
    private final LongSupplier nowMillis;

    private long lastTickAt;
    private boolean ticked = false;

    public SimulationScheduler(
            SimulationEngine engine, @Value("${dms.simulation.base-tick-ms:1000}") long baseTickMillis) {
        this(engine, baseTickMillis, System::currentTimeMillis);
    }

    SimulationScheduler(SimulationEngine engine, long baseTickMillis, LongSupplier nowMillis) {
        this.engine = engine;
        this.baseTickMillis = baseTickMillis;
        this.nowMillis = nowMillis;
    }

    @Scheduled(fixedRate = 250)
    public void beat() {
        if (!engine.isRunning() || engine.currentTick() >= Scenario.LENGTH) {
            return;
        }
        long interval = (long) (baseTickMillis / engine.speed());
        long now = nowMillis.getAsLong();
        if (!ticked || now - lastTickAt >= interval) {
            engine.advance();
            lastTickAt = now;
            ticked = true;
        }
    }
}
