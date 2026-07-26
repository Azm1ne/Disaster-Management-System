package bd.dms.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bd.dms.funds.Donation;
import bd.dms.funds.DonationRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DonationPatternDetectorTest {

    @Mock
    private DonationRepository donations;

    @Mock
    private AnomalyFlagRepository flags;

    private DonationPatternDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DonationPatternDetector(donations, flags);
    }

    @Test
    void isBurstIsFalseBelowThreshold() {
        Instant now = Instant.now();
        List<Instant> priors = List.of(now.minusSeconds(60), now.minusSeconds(120));
        assertThat(DonationPatternDetector.isBurst(priors, now, Duration.ofMinutes(10), 4)).isFalse();
    }

    @Test
    void isBurstIsTrueAtThreshold() {
        Instant now = Instant.now();
        List<Instant> priors = List.of(now.minusSeconds(60), now.minusSeconds(120), now.minusSeconds(180));
        assertThat(DonationPatternDetector.isBurst(priors, now, Duration.ofMinutes(10), 4)).isTrue();
    }

    @Test
    void isBurstIsTrueAboveThreshold() {
        Instant now = Instant.now();
        List<Instant> priors =
                List.of(now.minusSeconds(60), now.minusSeconds(120), now.minusSeconds(180), now.minusSeconds(240));
        assertThat(DonationPatternDetector.isBurst(priors, now, Duration.ofMinutes(10), 4)).isTrue();
    }

    @Test
    void isBurstIsFalseForATimestampJustOutsideTheWindow() {
        Instant now = Instant.now();
        List<Instant> priors = List.of(
                now.minusSeconds(60), now.minusSeconds(120), now.minus(Duration.ofMinutes(10)).minusSeconds(1));
        assertThat(DonationPatternDetector.isBurst(priors, now, Duration.ofMinutes(10), 4)).isFalse();
    }

    @Test
    void isBurstIsTrueForATimestampExactlyOnTheWindowBoundary() {
        Instant now = Instant.now();
        List<Instant> priors =
                List.of(now.minusSeconds(60), now.minusSeconds(120), now.minus(Duration.ofMinutes(10)));
        assertThat(DonationPatternDetector.isBurst(priors, now, Duration.ofMinutes(10), 4)).isTrue();
    }

    private Donation donationWithId(Long id, Long donorUserId, Instant createdAt) {
        Donation d = new Donation(donorUserId, 1L, BigDecimal.TEN);
        setId(d, id);
        setCreatedAt(d, createdAt);
        return d;
    }

    private void setId(Donation d, Long id) {
        try {
            var field = Donation.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(d, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void setCreatedAt(Donation d, Instant createdAt) {
        try {
            var field = Donation.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(d, createdAt);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void scanDonorFlagsTheFourthDonationWithinTheWindow() {
        Instant now = Instant.now();
        Donation newDonation = donationWithId(4L, 7L, now);
        List<Donation> priorDescending = List.of(
                donationWithId(3L, 7L, now.minusSeconds(60)),
                donationWithId(2L, 7L, now.minusSeconds(120)),
                donationWithId(1L, 7L, now.minusSeconds(180)));
        when(donations.findByDonorUserIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(newDonation, priorDescending.get(0), priorDescending.get(1), priorDescending.get(2)));
        when(flags.findByDetectorTypeOrderByCreatedAtDesc(AnomalyDetectorType.DONATION_PATTERN))
                .thenReturn(List.of());
        when(flags.save(any(AnomalyFlag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        detector.scanDonor(newDonation);

        verify(flags).save(any(AnomalyFlag.class));
    }

    @Test
    void scanDonorCreatesNoFlagForThreeDonationsSpreadFurtherApartThanTheWindow() {
        Instant now = Instant.now();
        Donation newDonation = donationWithId(3L, 7L, now);
        List<Donation> priorDescending = List.of(
                donationWithId(2L, 7L, now.minus(Duration.ofMinutes(20))),
                donationWithId(1L, 7L, now.minus(Duration.ofMinutes(40))));
        when(donations.findByDonorUserIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(newDonation, priorDescending.get(0), priorDescending.get(1)));

        detector.scanDonor(newDonation);

        verify(flags, never()).save(any(AnomalyFlag.class));
    }
}
