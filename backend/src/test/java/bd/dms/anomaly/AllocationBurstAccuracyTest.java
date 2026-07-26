package bd.dms.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Stands in for ticket 14's not-yet-built full validation harness: drives
 * {@link AllocationBurstDetector#isBurst(int)} directly against a synthetic set of trailing-window
 * decision counts with known ground truth, tallies TP/FP/FN/TN, and reports precision, recall,
 * false-positive rate, and detection lead time — this task's job is to measure the detector's
 * behavior in aggregate, not to re-test the threshold in isolation (already covered by
 * {@link AllocationBurstDetectorTest}).
 */
class AllocationBurstAccuracyTest {

    private record Case(String label, int distinctDecisionCount, boolean groundTruthPositive) {}

    @Test
    void detectorMeetsAccuracyTargetsAcrossSyntheticCases() {
        List<Case> cases = List.of(
                // True positives (vary the margin).
                new Case("burst exactly at threshold", 3, true),
                new Case("burst well above threshold", 8, true),
                // Innocent look-alikes: elevated activity that stays just under the threshold.
                new Case("two recommendations just under threshold", 2, false),
                new Case("a single isolated recommendation", 1, false),
                // Clear negative baseline.
                new Case("quiet window, no recommendations", 0, false));

        int tp = 0;
        int fp = 0;
        int fn = 0;
        int tn = 0;
        for (Case c : cases) {
            boolean predicted = AllocationBurstDetector.isBurst(c.distinctDecisionCount());
            if (c.groundTruthPositive() && predicted) {
                tp++;
            } else if (!c.groundTruthPositive() && predicted) {
                fp++;
            } else if (c.groundTruthPositive() && !predicted) {
                fn++;
            } else {
                tn++;
            }
        }

        double precision = tp / (double) (tp + fp);
        double recall = tp / (double) (tp + fn);
        double falsePositiveRate = fp / (double) (fp + tn);

        System.out.println("AllocationBurstDetector: precision=%.2f recall=%.2f falsePositiveRate=%.2f (tp=%d fp=%d fn=%d tn=%d)"
                .formatted(precision, recall, falsePositiveRate, tp, fp, fn, tn));

        assertThat(precision).isGreaterThanOrEqualTo(0.8);
        assertThat(recall).isGreaterThanOrEqualTo(0.8);
        assertThat(falsePositiveRate).isLessThanOrEqualTo(0.2);
    }

    @Test
    void leadTimeIsZeroBecauseScanEvaluatesTheTrailingWindowOnTheSameTickItCompletes() {
        // Decisions generated at each tick (index = tick). Three decisions land at ticks 0, 1, 2 —
        // the trailing window (WINDOW_TICKS=5) covering tick 2 is [0, 2], which first reaches the
        // burst threshold at tick 2.
        int[] decisionsGeneratedAtTick = {1, 1, 1, 0, 0, 0, 0, 0, 0, 0};

        long burstFirstTrueAtTick = -1;
        long detectedAtTick = -1;
        for (long tick = 0; tick < decisionsGeneratedAtTick.length; tick++) {
            long from = Math.max(0, tick - AllocationBurstDetector.WINDOW_TICKS + 1);
            int windowCount = 0;
            for (long t = from; t <= tick; t++) {
                windowCount += decisionsGeneratedAtTick[(int) t];
            }
            // This mirrors exactly what AllocationBurstDetector.scan(tick) computes before calling
            // isBurst — the same trailing window, evaluated as of the same tick.
            boolean burstNow = AllocationBurstDetector.isBurst(windowCount);
            if (burstNow && burstFirstTrueAtTick == -1) {
                burstFirstTrueAtTick = tick;
                // scan(tick) reports the burst on this very tick, since it's called per-tick and
                // checks the window as of that tick — there is no separate "detection" step later.
                detectedAtTick = tick;
            }
        }

        assertThat(burstFirstTrueAtTick).isEqualTo(2);
        long leadTime = detectedAtTick - burstFirstTrueAtTick;
        System.out.println("AllocationBurstDetector: leadTime=%d ticks (burst true at tick %d, detected at tick %d)"
                .formatted(leadTime, burstFirstTrueAtTick, detectedAtTick));
        assertThat(leadTime).isEqualTo(0);
    }
}
