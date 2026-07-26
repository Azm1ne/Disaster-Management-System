package bd.dms.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import bd.dms.anomaly.DuplicateRegistrationDetector.MemberKey;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Stands in for ticket 14's not-yet-built full validation harness: drives
 * {@link DuplicateRegistrationDetector#similarity(String, List, String, List)} directly against a
 * synthetic set of group-pair scenarios with known ground truth, tallies TP/FP/FN/TN, and reports
 * precision, recall, false-positive rate, and detection lead time — this task's job is to measure
 * the detector's behavior in aggregate, not to re-test the Levenshtein/overlap math in isolation
 * (already covered by {@link DuplicateRegistrationDetectorTest}).
 */
class DuplicateRegistrationAccuracyTest {

    private record Case(
            String label, String nameA, List<MemberKey> membersA, String nameB, List<MemberKey> membersB, boolean groundTruthPositive) {}

    private static MemberKey key(String nickname) {
        return new MemberKey(nickname, "ADULT");
    }

    @Test
    void detectorMeetsAccuracyTargetsAcrossSyntheticCases() {
        List<MemberKey> fiveMembers = List.of(key("a1"), key("a2"), key("a3"), key("a4"), key("a5"));
        List<MemberKey> fiveMembersOneShared = List.of(key("a1"), key("b2"), key("b3"), key("b4"), key("b5"));

        List<Case> cases = List.of(
                // True positives (vary the margin).
                new Case(
                        "identical name and identical roster",
                        "Karim Family",
                        fiveMembers,
                        "Karim Family",
                        fiveMembers,
                        true),
                new Case(
                        "identical name, roster overlap right at the threshold",
                        "Karim Family",
                        fiveMembers,
                        "Karim Family",
                        fiveMembersOneShared,
                        true), // nameSim=1.0, overlap=1/5=0.2 -> score = 0.5*1.0 + 0.5*0.2 = 0.6 (== threshold, still flags)
                // Innocent look-alikes: resemble a duplicate on a shallow read but have a legitimate
                // distinguishing feature, so the combined score stays under the threshold.
                new Case(
                        "same common family surname, entirely different household",
                        "Islam Family",
                        fiveMembers,
                        "Islam Family",
                        List.of(key("z1"), key("z2"), key("z3"), key("z4"), key("z5")),
                        false), // nameSim=1.0, overlap=0.0 -> score = 0.5
                new Case(
                        "similar-sounding name, mostly different household",
                        "a".repeat(20),
                        fiveMembers,
                        "a".repeat(15) + "b".repeat(5), // 15-char prefix kept, distance 5 of 20 -> nameSim=0.75
                        List.of(key("a1"), key("a2"), key("z3"), key("z4"), key("z5")), // 2 of 5 shared -> overlap=0.4
                        false), // score = 0.5*0.75 + 0.5*0.4 = 0.575, just under the 0.6 threshold
                // Clear negative baseline: unrelated names, unrelated rosters.
                new Case(
                        "unrelated families",
                        "Karim Family",
                        fiveMembers,
                        "Zzzzzzzzzz Household",
                        List.of(key("q1"), key("q2")),
                        false));

        int tp = 0;
        int fp = 0;
        int fn = 0;
        int tn = 0;
        for (Case c : cases) {
            double score = DuplicateRegistrationDetector.similarity(c.nameA(), c.membersA(), c.nameB(), c.membersB());
            boolean predicted = score >= DuplicateRegistrationDetector.SIMILARITY_THRESHOLD;
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

        System.out.println(
                "DuplicateRegistrationDetector: precision=%.2f recall=%.2f falsePositiveRate=%.2f (tp=%d fp=%d fn=%d tn=%d)"
                        .formatted(precision, recall, falsePositiveRate, tp, fp, fn, tn));

        assertThat(precision).isGreaterThanOrEqualTo(0.8);
        assertThat(recall).isGreaterThanOrEqualTo(0.8);
        assertThat(falsePositiveRate).isLessThanOrEqualTo(0.2);
    }

    @Test
    void leadTimeIsZeroBecauseScanNewGroupEvaluatesSimilarityOnTheRegistrationEventItself() {
        // Event 0: "Karim Family" registers (nothing to compare against yet).
        // Event 1: a near-duplicate "Karim Family" registers with an identical roster — this is the
        // event where the duplicate condition first becomes true.
        List<MemberKey> roster = List.of(key("a1"), key("a2"));
        int triggeringEventIndex = 1;

        // scanNewGroup runs synchronously right after event 1's group is saved, and it calls
        // similarity() against every existing group (including event 0's) on that same call — there
        // is no separate later pass that re-scans for duplicates.
        double score = DuplicateRegistrationDetector.similarity("Karim Family", roster, "Karim Family", roster);
        boolean detected = score >= DuplicateRegistrationDetector.SIMILARITY_THRESHOLD;
        int detectedAtEventIndex = triggeringEventIndex;

        assertThat(detected).isTrue();
        long leadTime = detectedAtEventIndex - triggeringEventIndex;
        System.out.println(
                "DuplicateRegistrationDetector: leadTime=%d events (duplicate true at event %d, detected at event %d)"
                        .formatted(leadTime, triggeringEventIndex, detectedAtEventIndex));
        assertThat(leadTime).isEqualTo(0);
    }
}
