package bd.dms.allocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

import bd.dms.family.FamilyMemberRepository;
import bd.dms.forecast.ForecastResult;
import bd.dms.forecast.ForecastService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AllocationScoringServiceTest {

    @Mock
    private ForecastService forecastService;

    @Mock
    private FamilyMemberRepository familyMembers;

    @Mock
    private AllocationDecisionRepository allocations;

    private AllocationScoringService scoring;

    @BeforeEach
    void setUp() {
        scoring = new AllocationScoringService(forecastService, familyMembers, allocations);
    }

    private ForecastResult forecast(BigDecimal currentQuantity, BigDecimal ratePerTick, Long worstCase) {
        return new ForecastResult(
                1L, "WATER", currentQuantity, ratePerTick, worstCase, worstCase, worstCase, 1.0, "HIGH", 5, 3);
    }

    @Test
    void surplusCampHasZeroGapAndPositiveSurplus() {
        when(forecastService.forecast(1L, "WATER", 20L))
                .thenReturn(forecast(BigDecimal.valueOf(80), BigDecimal.valueOf(5), 16L));

        AllocationScoringService.CampResourceState state = scoring.resourceState(1L, "WATER", 20L);

        assertThat(state.reserve()).isEqualByComparingTo(BigDecimal.valueOf(50)); // 5 * 10
        assertThat(state.gap()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(state.surplus()).isEqualByComparingTo(BigDecimal.valueOf(30)); // 80 - 50
    }

    @Test
    void shortageCampHasPositiveGapAndZeroSurplus() {
        when(forecastService.forecast(1L, "WATER", 20L))
                .thenReturn(forecast(BigDecimal.valueOf(20), BigDecimal.valueOf(5), 4L));

        AllocationScoringService.CampResourceState state = scoring.resourceState(1L, "WATER", 20L);

        assertThat(state.gap()).isEqualByComparingTo(BigDecimal.valueOf(30)); // 50 - 20
        assertThat(state.surplus()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void nonDepletingResourceHasZeroReserveAndFullSurplus() {
        when(forecastService.forecast(1L, "WATER", 20L))
                .thenReturn(forecast(BigDecimal.valueOf(40), BigDecimal.ZERO, null));

        AllocationScoringService.CampResourceState state = scoring.resourceState(1L, "WATER", 20L);

        assertThat(state.reserve()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(state.gap()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(state.surplus()).isEqualByComparingTo(BigDecimal.valueOf(40));
    }

    @Test
    void severityScoreIsTheMedicalFlagRatioAndZeroWhenNoMembers() {
        when(familyMembers.countByCampId(5L)).thenReturn(10L);
        when(familyMembers.countMedicalFlagTrueByCampId(5L)).thenReturn(3L);
        assertThat(scoring.severityScore(5L)).isCloseTo(0.3, within(0.0001));

        when(familyMembers.countByCampId(6L)).thenReturn(0L);
        assertThat(scoring.severityScore(6L)).isEqualTo(0.0);
    }

    @Test
    void shortageScoreClampsToZeroOneAndIsZeroWithNoHorizon() {
        assertThat(scoring.shortageScore(3L)).isCloseTo(0.5, within(0.0001)); // 1 - 3/6
        assertThat(scoring.shortageScore(0L)).isCloseTo(1.0, within(0.0001));
        assertThat(scoring.shortageScore(12L)).isEqualTo(0.0); // clamped, would be negative
        assertThat(scoring.shortageScore(null)).isEqualTo(0.0);
    }

    @Test
    void ticksSinceLastApprovedIsNullWhenNeverApprovedElseTicksSinceTheLatest() {
        when(allocations.findByTargetCampIdAndResourceTypeAndStatusIn(
                        7L, "FOOD", List.of(AllocationStatus.APPROVED, AllocationStatus.MODIFIED)))
                .thenReturn(List.of());
        assertThat(scoring.ticksSinceLastApproved(7L, "FOOD", 15L)).isNull();

        AllocationDecision decidedAt5 = new AllocationDecision(
                "FOOD", 1L, 7L, BigDecimal.TEN, 0, 0, 0, 0, 0, 4);
        decidedAt5.applyDecision(AllocationStatus.APPROVED, BigDecimal.TEN, 5L);
        AllocationDecision decidedAt9 = new AllocationDecision(
                "FOOD", 2L, 7L, BigDecimal.TEN, 0, 0, 0, 0, 0, 8);
        decidedAt9.applyDecision(AllocationStatus.MODIFIED, BigDecimal.ONE, 9L);
        when(allocations.findByTargetCampIdAndResourceTypeAndStatusIn(
                        7L, "FOOD", List.of(AllocationStatus.APPROVED, AllocationStatus.MODIFIED)))
                .thenReturn(List.of(decidedAt5, decidedAt9));

        assertThat(scoring.ticksSinceLastApproved(7L, "FOOD", 15L)).isEqualTo(6L); // 15 - 9
    }

    @Test
    void priorityScoreIsTheDocumentedWeightedSum() {
        double score = scoring.priorityScore(1.0, 1.0, 1.0, 1.0);
        assertThat(score).isCloseTo(1.0, within(0.0001));

        double partial = scoring.priorityScore(0.5, 0.0, 0.0, 0.0);
        assertThat(partial).isCloseTo(0.30 * 0.5, within(0.0001));
    }
}
