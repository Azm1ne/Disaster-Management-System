package bd.dms.sim;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The scheduler's timing logic, tested deterministically with a mocked engine and a hand-cranked
 * clock — no real scheduling. Confirms: a playing run advances once per interval, a paused run
 * never advances, a higher speed advances more often (cadence, not state), and a finished run
 * stops advancing.
 */
@ExtendWith(MockitoExtension.class)
class SimulationSchedulerTest {

    private static final long BASE = 1000;

    @Mock
    private SimulationEngine engine;

    private final AtomicLong now = new AtomicLong(0);

    private SimulationScheduler scheduler() {
        return new SimulationScheduler(engine, BASE, now::get);
    }

    @Test
    void advancesOncePerIntervalWhilePlaying() {
        when(engine.isRunning()).thenReturn(true);
        when(engine.currentTick()).thenReturn(0L);
        when(engine.speed()).thenReturn(1.0);
        SimulationScheduler scheduler = scheduler();

        now.set(0);
        scheduler.beat(); // first due beat
        now.set(500);
        scheduler.beat(); // not yet a full interval since last tick
        now.set(1000);
        scheduler.beat(); // a full interval later

        verify(engine, times(2)).advance();
    }

    @Test
    void pausedRunNeverAdvances() {
        when(engine.isRunning()).thenReturn(false);
        SimulationScheduler scheduler = scheduler();

        now.set(5000);
        scheduler.beat();
        now.set(9000);
        scheduler.beat();

        verify(engine, never()).advance();
    }

    @Test
    void higherSpeedAdvancesMoreOften() {
        when(engine.isRunning()).thenReturn(true);
        when(engine.currentTick()).thenReturn(0L);
        when(engine.speed()).thenReturn(4.0); // interval becomes 250ms
        SimulationScheduler scheduler = scheduler();

        now.set(0);
        scheduler.beat();
        now.set(250);
        scheduler.beat();
        now.set(500);
        scheduler.beat();

        verify(engine, times(3)).advance();
    }

    @Test
    void finishedRunStopsAdvancing() {
        when(engine.isRunning()).thenReturn(true);
        when(engine.currentTick()).thenReturn(Scenario.LENGTH);
        SimulationScheduler scheduler = scheduler();

        now.set(10_000);
        scheduler.beat();

        verify(engine, never()).advance();
    }
}
