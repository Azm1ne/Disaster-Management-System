package bd.dms.anomaly;

import bd.dms.funds.Donation;
import bd.dms.funds.DonationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Flags a donor making several donations within a short time window — a structuring-like burst.
 * Runs synchronously right after {@code FundLedgerService.donate} persists the new donation.
 * Never mutates {@code donations} — only reads it and writes a new {@link AnomalyFlag}.
 */
@Component
public class DonationPatternDetector {

    static final int BURST_COUNT = 4;
    static final Duration BURST_WINDOW = Duration.ofMinutes(10);

    private static final String INNOCENT_EXPLANATION =
            "A generous donor topping up their contribution in several installments looks "
                    + "identical to structuring — this may simply be someone donating multiple "
                    + "times because they trust the response.";

    private final DonationRepository donations;
    private final AnomalyFlagRepository flags;

    public DonationPatternDetector(DonationRepository donations, AnomalyFlagRepository flags) {
        this.donations = donations;
        this.flags = flags;
    }

    /** Pure, DB-free burst check: how many of {@code priorDonorTimestamps} plus the newest
     * donation itself fall within {@code window} of {@code newest}. */
    public static boolean isBurst(
            List<Instant> priorDonorTimestamps, Instant newest, Duration window, int threshold) {
        long countInWindow = priorDonorTimestamps.stream()
                .filter(t -> !t.isBefore(newest.minus(window)))
                .count()
                + 1; // +1 for the newest donation itself
        return countInWindow >= threshold;
    }

    public void scanDonor(Donation newDonation) {
        Long donorUserId = newDonation.getDonorUserId();
        List<Donation> priorForDonor = donations.findByDonorUserIdOrderByCreatedAtDesc(donorUserId).stream()
                .filter(d -> !d.getId().equals(newDonation.getId()))
                .toList();

        List<Instant> priorTimestamps = priorForDonor.stream().map(Donation::getCreatedAt).toList();
        if (!isBurst(priorTimestamps, newDonation.getCreatedAt(), BURST_WINDOW, BURST_COUNT)) {
            return;
        }

        if (alreadyFlaggedRecently(priorForDonor, newDonation)) {
            return;
        }

        Instant windowStart = newDonation.getCreatedAt().minus(BURST_WINDOW);
        List<Long> subjectIds = new ArrayList<>(priorForDonor.stream()
                .filter(d -> !d.getCreatedAt().isBefore(windowStart))
                .map(Donation::getId)
                .toList());
        subjectIds.add(newDonation.getId());

        int countInWindow = subjectIds.size();
        double score = Math.min(1.0, countInWindow / (double) (BURST_COUNT * 2));
        String summary = "Donor %d made %d donations within %d minutes"
                .formatted(donorUserId, countInWindow, BURST_WINDOW.toMinutes());

        AnomalyFlag flag = new AnomalyFlag(
                AnomalyDetectorType.DONATION_PATTERN, score, summary, INNOCENT_EXPLANATION, subjectIds, null);
        flags.save(flag);
    }

    /** Skip if the most recent {@code DONATION_PATTERN} flag covering one of this donor's
     * donations (prior or new) was created within {@code BURST_WINDOW} of now — the simplest
     * correct de-dup check without tracking which prior flags a given donation already belongs to. */
    private boolean alreadyFlaggedRecently(List<Donation> priorForDonor, Donation newDonation) {
        List<Long> donorDonationIds = new ArrayList<>(priorForDonor.stream().map(Donation::getId).toList());
        donorDonationIds.add(newDonation.getId());

        return flags.findByDetectorTypeOrderByCreatedAtDesc(AnomalyDetectorType.DONATION_PATTERN).stream()
                .filter(f -> f.getStatus() == AnomalyFlagStatus.OPEN || f.getStatus() == AnomalyFlagStatus.CONFIRMED)
                .filter(f -> f.getSubjectIds().stream().anyMatch(donorDonationIds::contains))
                .findFirst()
                .map(f -> f.getCreatedAt().isAfter(Instant.now().minus(BURST_WINDOW)))
                .orElse(false);
    }
}
