package bd.dms.alert;

import bd.dms.sim.SimulationEngine;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The SLA sweep: on a wall-clock beat, checks every alert still in {@code NEW}/
 * {@code ACKNOWLEDGED} against the simulation's current tick and escalates anything past its
 * deadline. Runs alongside {@code SimulationScheduler}'s own beat rather than being tied to it,
 * since an alert can go stale even while the simulation is paused mid-demo.
 */
@Component
public class AlertSlaScheduler {

    private final AlertService alertService;
    private final SimulationEngine engine;

    public AlertSlaScheduler(AlertService alertService, SimulationEngine engine) {
        this.alertService = alertService;
        this.engine = engine;
    }

    @Scheduled(fixedRate = 500)
    public void sweep() {
        alertService.escalateStale(engine.currentTick());
    }
}
