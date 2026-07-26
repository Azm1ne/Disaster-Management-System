package bd.dms.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bd.dms.allocation.AllocationDecision;
import bd.dms.allocation.AllocationDecisionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AllocationBurstDetectorTest {

    @Mock
    private AllocationDecisionRepository allocations;

    @Mock
    private AnomalyFlagRepository flags;

    private AllocationBurstDetector detector;

    @BeforeEach
    void setUp() {
        detector = new AllocationBurstDetector(allocations, flags);
    }

    private AllocationDecision decision(long tick) {
        return new AllocationDecision(
                "WATER", 1L, 2L, BigDecimal.TEN, 0.5, 0.5, 0.5, 0.5, 0.5, tick);
    }

    @Test
    void isBurstIsFalseBelowThreshold() {
        assertThat(AllocationBurstDetector.isBurst(0)).isFalse();
        assertThat(AllocationBurstDetector.isBurst(2)).isFalse();
    }

    @Test
    void isBurstIsTrueAtAndAboveThreshold() {
        assertThat(AllocationBurstDetector.isBurst(3)).isTrue();
        assertThat(AllocationBurstDetector.isBurst(4)).isTrue();
    }

    @Test
    void scanCreatesNoFlagWhenBelowThreshold() {
        when(allocations.findByGeneratedAtTickBetween(6L, 10L)).thenReturn(List.of(decision(10), decision(10)));

        Optional<AnomalyFlag> result = detector.scan(10L);

        assertThat(result).isEmpty();
    }

    @Test
    void scanCreatesAFlagAtThreshold() {
        when(allocations.findByGeneratedAtTickBetween(6L, 10L))
                .thenReturn(List.of(decision(10), decision(10), decision(10)));
        when(flags.findByDetectorTypeOrderByCreatedAtDesc(AnomalyDetectorType.ALLOCATION_BURST))
                .thenReturn(List.of());
        when(flags.save(any(AnomalyFlag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<AnomalyFlag> result = detector.scan(10L);

        assertThat(result).isPresent();
        AnomalyFlag flag = result.get();
        assertThat(flag.getDetectorType()).isEqualTo(AnomalyDetectorType.ALLOCATION_BURST);
        assertThat(flag.getDetectedAtTick()).isEqualTo(10L);
        assertThat(flag.getScore()).isEqualTo(0.5);
        assertThat(flag.getInnocentExplanation()).isNotBlank();
        assertThat(flag.getSummary()).contains("3").contains("tick 6-10");
    }

    @Test
    void scanDoesNotCreateASecondFlagForTheSameTick() {
        when(allocations.findByGeneratedAtTickBetween(6L, 10L))
                .thenReturn(List.of(decision(10), decision(10), decision(10)));
        AnomalyFlag existing = new AnomalyFlag(
                AnomalyDetectorType.ALLOCATION_BURST, 0.5, "existing", "explanation", List.of(1L), 10L);
        when(flags.findByDetectorTypeOrderByCreatedAtDesc(AnomalyDetectorType.ALLOCATION_BURST))
                .thenReturn(List.of(existing));

        Optional<AnomalyFlag> result = detector.scan(10L);

        assertThat(result).isEmpty();
    }
}
