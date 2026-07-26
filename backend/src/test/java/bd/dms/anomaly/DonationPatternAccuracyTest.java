package bd.dms.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Stands in for ticket 14's not-yet-built full validation harness: drives
 * {@link DonationPatternDetector#isBurst(List, Instant, Duration, int)} directly against a
 * synthetic set of donor timing patterns with known ground truth, tallies TP/FP/FN/TN, and reports
 * precision, recall, false-positive rate, and detection lead time — this task's job is to measure
 * the detector's behavior in aggregate, not to re-test the window boundary in isolation (already
 * covered by {@link DonationPatternDetectorTest}).
 */
class DonationPatternAccuracyTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private record Case(String label, List<Instant> priorTimestamps, Instant newest, boolean groundTruthPositive) {}

    @Test
    void detectorMeetsAccuracyTargetsAcrossSyntheticCases() {
        List<Case> cases = List.of(
                // True positives (vary the margin).
                new Case(
                        "four donations exactly filling the window",
                        List.of(NOW.minusSeconds(60), NOW.minusSeconds(180), NOW.minusSeconds(300)),
                        NOW,
                        true),
                new Case(
                        "seven donations, well above the burst count",
                        List.of(
                                NOW.minusSeconds(30),
                                NOW.minusSeconds(60),
                                NOW.minusSeconds(90),
                                NOW.minusSeconds(120),
                                NOW.minusSeconds(150),
                                NOW.minusSeconds(180)),
                        NOW,
                        true),
                // Innocent look-alikes: multiple donations, but not enough within the window to trip it.
                new Case(
                        "three top-ups spread a little wider than the window",
                        List.of(NOW.minusSeconds(60), NOW.minus(Duration.ofMinutes(12)), NOW.minus(Duration.ofMinutes(20))),
                        NOW,
                        false),
                new Case(
                        "two donations right at the edge of the window",
                        List.of(NOW.minusSeconds(60), NOW.minus(Duration.ofMinutes(10)).minusSeconds(1)),
                        NOW,
                        false),
                // Clear negative baseline: a donor's very first donation.
                new Case("first-ever donation, no prior history", List.of(), NOW, false));

        int tp = 0;
        int fp = 0;
        int fn = 0;
        int tn = 0;
        for (Case c : cases) {
            boolean predicted = DonationPatternDetector.isBurst(
                    c.priorTimestamps(), c.newest(), DonationPatternDetector.BURST_WINDOW, DonationPatternDetector.BURST_COUNT);
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

        System.out.println("DonationPatternDetector: precision=%.2f recall=%.2f falsePositiveRate=%.2f (tp=%d fp=%d fn=%d tn=%d)"
                .formatted(precision, recall, falsePositiveRate, tp, fp, fn, tn));

        assertThat(precision).isGreaterThanOrEqualTo(0.8);
        assertThat(recall).isGreaterThanOrEqualTo(0.8);
        assertThat(falsePositiveRate).isLessThanOrEqualTo(0.2);
    }

    @Test
    void leadTimeIsZeroBecauseIsBurstEvaluatesEveryNewDonationImmediately() {
        // Four donations from the same donor, two minutes apart, all inside the ten-minute window.
        // The burst condition first becomes true on the fourth donation (event index 3) — the same
        // event scanDonor evaluates it on, since it always checks "prior donations + this one".
        List<Instant> donationTimestamps = List.of(
                NOW, NOW.plusSeconds(120), NOW.plusSeconds(240), NOW.plusSeconds(360));

        long burstFirstTrueAtEventIndex = -1;
        long detectedAtEventIndex = -1;
        for (int i = 0; i < donationTimestamps.size(); i++) {
            Instant newest = donationTimestamps.get(i);
            List<Instant> priors = donationTimestamps.subList(0, i);
            boolean burstNow = DonationPatternDetector.isBurst(
                    priors, newest, DonationPatternDetector.BURST_WINDOW, DonationPatternDetector.BURST_COUNT);
            if (burstNow && burstFirstTrueAtEventIndex == -1) {
                burstFirstTrueAtEventIndex = i;
                // scanDonor(newDonation) evaluates isBurst synchronously on this same donation event.
                detectedAtEventIndex = i;
            }
        }

        assertThat(burstFirstTrueAtEventIndex).isEqualTo(3);
        long leadTime = detectedAtEventIndex - burstFirstTrueAtEventIndex;
        System.out.println(
                "DonationPatternDetector: leadTime=%d events (burst true at event %d, detected at event %d)"
                        .formatted(leadTime, burstFirstTrueAtEventIndex, detectedAtEventIndex));
        assertThat(leadTime).isEqualTo(0);
    }
}
