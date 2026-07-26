package bd.dms.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * anomaly_flags has no FK-constrained columns of its own (subject_ids are opaque evidence row
 * ids, meaning depends on detector_type), so unlike AllocationDecisionRepositoryTest/
 * FamilyMemberRepositoryTest this needs no seeded fixtures — a bare flag is enough.
 */
@SpringBootTest
class AnomalyFlagRepositoryTest {

    @Autowired
    private AnomalyFlagRepository flags;

    @Test
    void savesAndReadsBackSubjectIdsAndDedupesDoubleDisposal() {
        AnomalyFlag flag = new AnomalyFlag(
                AnomalyDetectorType.ALLOCATION_BURST,
                0.75,
                "4 allocation recommendations created for WATER across 3 camps within 5 ticks",
                "A genuine region-wide shortage can also produce several recommendations in a short span.",
                List.of(101L, 102L),
                42L);

        AnomalyFlag saved = flags.save(flag);
        Long id = saved.getId();

        AnomalyFlag reloaded = flags.findById(id).orElseThrow();
        assertThat(reloaded.getDetectorType()).isEqualTo(AnomalyDetectorType.ALLOCATION_BURST);
        assertThat(reloaded.getStatus()).isEqualTo(AnomalyFlagStatus.OPEN);
        assertThat(reloaded.getSubjectIds()).containsExactlyInAnyOrder(101L, 102L);
        assertThat(reloaded.getDetectedAtTick()).isEqualTo(42L);

        reloaded.dispose(AnomalyFlagStatus.CONFIRMED, 7L, "looks real");
        assertThat(reloaded.getStatus()).isEqualTo(AnomalyFlagStatus.CONFIRMED);
        assertThat(reloaded.getReviewedByUserId()).isEqualTo(7L);
        assertThat(reloaded.getReviewNote()).isEqualTo("looks real");
        assertThat(reloaded.getReviewedAt()).isNotNull();

        assertThatThrownBy(() -> reloaded.dispose(AnomalyFlagStatus.DISMISSED, 7L, "changed my mind"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsDisposingToANonTerminalStatus() {
        AnomalyFlag flag = new AnomalyFlag(
                AnomalyDetectorType.DONATION_PATTERN,
                0.5,
                "summary",
                "innocent explanation",
                List.of(1L),
                null);

        assertThatThrownBy(() -> flag.dispose(AnomalyFlagStatus.OPEN, 1L, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
