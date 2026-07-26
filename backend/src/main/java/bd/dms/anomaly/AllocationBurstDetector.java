package bd.dms.anomaly;

import bd.dms.allocation.AllocationDecision;
import bd.dms.allocation.AllocationDecisionRepository;
import bd.dms.sim.WorldChangedEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Flags a burst of {@link AllocationDecision} recommendations generated within a short trailing
 * window of ticks — same {@link WorldChangedEvent} hook {@code AllocationGenerationService} uses,
 * so a burst is detected the same tick it happens (see {@link #scan}). Never mutates
 * {@code allocation_decisions} — only reads it and writes a new {@link AnomalyFlag}.
 */
@Component
public class AllocationBurstDetector {

    static final int WINDOW_TICKS = 5;
    static final int BURST_THRESHOLD = 3;

    private static final String INNOCENT_EXPLANATION =
            "A genuine region-wide shortage can also produce several recommendations in a short span as "
                    + "multiple camps cross their reserve threshold around the same time — this is not "
                    + "necessarily a coordinated or erroneous spike.";

    private final AllocationDecisionRepository allocations;
    private final AnomalyFlagRepository flags;

    public AllocationBurstDetector(AllocationDecisionRepository allocations, AnomalyFlagRepository flags) {
        this.allocations = allocations;
        this.flags = flags;
    }

    /** Pure threshold check, kept trivial and DB-free so it can be unit-tested directly and reused
     * by the accuracy tests — window selection lives in {@link #scan}, not here. */
    static boolean isBurst(int distinctDecisionCount) {
        return distinctDecisionCount >= BURST_THRESHOLD;
    }

    @EventListener
    public void onWorldChanged(WorldChangedEvent event) {
        if (!event.worldChanged()) {
            return;
        }
        scan(event.clock().tick());
    }

    public Optional<AnomalyFlag> scan(long tick) {
        long from = Math.max(0, tick - WINDOW_TICKS + 1);
        List<AllocationDecision> recent = allocations.findByGeneratedAtTickBetween(from, tick);
        if (!isBurst(recent.size())) {
            return Optional.empty();
        }

        boolean alreadyFlaggedThisTick = flags.findByDetectorTypeOrderByCreatedAtDesc(AnomalyDetectorType.ALLOCATION_BURST)
                .stream()
                .anyMatch(flag -> flag.getDetectedAtTick() != null && flag.getDetectedAtTick() == tick);
        if (alreadyFlaggedThisTick) {
            return Optional.empty();
        }

        String summary = "%d allocation recommendations generated within the last %d ticks (tick %d-%d)"
                .formatted(recent.size(), WINDOW_TICKS, from, tick);
        List<Long> subjectIds = recent.stream().map(AllocationDecision::getId).toList();
        double score = Math.min(1.0, recent.size() / (double) (BURST_THRESHOLD * 2));

        AnomalyFlag flag = new AnomalyFlag(
                AnomalyDetectorType.ALLOCATION_BURST, score, summary, INNOCENT_EXPLANATION, subjectIds, tick);
        return Optional.of(flags.save(flag));
    }
}
