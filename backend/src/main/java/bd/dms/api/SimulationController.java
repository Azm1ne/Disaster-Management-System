package bd.dms.api;

import bd.dms.sim.SimulationClock;
import bd.dms.sim.SimulationEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The demo-only simulation control surface. Reading the clock is open to any signed-in user, so
 * every screen can show the same DEMO clock; driving the simulation (pause, resume, speed, reset)
 * is restricted to Admin and Coordinator in {@code SecurityConfig} — the UI disabling a button is
 * a convenience, this is the boundary.
 */
@RestController
@RequestMapping("/simulation")
public class SimulationController {

    /** The requested playback multiplier; the engine rejects anything outside its allowed set. */
    public record SpeedRequest(double multiplier) {}

    private final SimulationEngine engine;

    public SimulationController(SimulationEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/clock")
    public SimulationClock clock() {
        return engine.clock();
    }

    @PostMapping("/pause")
    public SimulationClock pause() {
        return engine.pause();
    }

    @PostMapping("/resume")
    public SimulationClock resume() {
        return engine.resume();
    }

    @PostMapping("/reset")
    public SimulationClock reset() {
        return engine.reset();
    }

    @PostMapping("/speed")
    public SimulationClock speed(@RequestBody SpeedRequest request) {
        return engine.setSpeed(request.multiplier());
    }
}
