package bd.dms.allocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Mocks {@link AllocationScoringService} and the repositories to exercise the pairing/dedup
 * algorithm directly, since the real scripted {@code Scenario} never drives any camp/resource
 * into an actual gap (confirmed separately in {@code AllocationGenerationIntegrationTest}) — the
 * same demo-stability property {@code ForecastAlertListener} already relies on a DEMO trigger to
 * work around.
 */
@ExtendWith(MockitoExtension.class)
class AllocationGenerationServiceTest {

    @Mock
    private CampRepository camps;

    @Mock
    private AllocationScoringService scoring;

    @Mock
    private AllocationDecisionRepository allocations;

    private AllocationGenerationService generation;

    @BeforeEach
    void setUp() {
        generation = new AllocationGenerationService(camps, scoring, allocations);
    }

    private Camp camp(long id, String code, int population) {
        Camp camp = mock(Camp.class);
        when(camp.getId()).thenReturn(id);
        Mockito.lenient().when(camp.getCode()).thenReturn(code);
        Mockito.lenient().when(camp.getPopulation()).thenReturn(population);
        return camp;
    }

    private void stubAllResourceTypesNoGapNoSurplus(long campId) {
        for (String rt : List.of("WATER", "FOOD", "MEDICAL")) {
            when(scoring.resourceState(campId, rt, 20L)).thenReturn(new AllocationScoringService.CampResourceState(
                    campId, rt, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, 10L));
        }
    }

    @Test
    void pairsAShortageCampWithItsBestSurplusSourceAndSavesOneRecommendation() {
        Camp shortageCamp = camp(1L, "shortage", 100);
        Camp weakSurplusCamp = camp(2L, "weak-surplus", 50);
        Camp strongSurplusCamp = camp(3L, "strong-surplus", 50);
        when(camps.findAll()).thenReturn(List.of(shortageCamp, weakSurplusCamp, strongSurplusCamp));

        stubAllResourceTypesNoGapNoSurplus(1L);
        stubAllResourceTypesNoGapNoSurplus(2L);
        stubAllResourceTypesNoGapNoSurplus(3L);
        // Override WATER for the three camps under test.
        when(scoring.resourceState(1L, "WATER", 20L)).thenReturn(new AllocationScoringService.CampResourceState(
                1L, "WATER", BigDecimal.valueOf(10), BigDecimal.valueOf(50), BigDecimal.valueOf(40), BigDecimal.ZERO, 3L));
        when(scoring.resourceState(2L, "WATER", 20L)).thenReturn(new AllocationScoringService.CampResourceState(
                2L, "WATER", BigDecimal.valueOf(60), BigDecimal.valueOf(50), BigDecimal.ZERO, BigDecimal.valueOf(10), 20L));
        when(scoring.resourceState(3L, "WATER", 20L)).thenReturn(new AllocationScoringService.CampResourceState(
                3L, "WATER", BigDecimal.valueOf(80), BigDecimal.valueOf(50), BigDecimal.ZERO, BigDecimal.valueOf(30), 20L));

        when(scoring.severityScore(1L)).thenReturn(0.2);
        when(scoring.shortageScore(3L)).thenReturn(0.9);
        when(scoring.ticksSinceLastApproved(1L, "WATER", 20L)).thenReturn(5L);
        when(scoring.priorityScore(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(0.5);
        when(allocations.findBySourceCampIdAndTargetCampIdAndResourceTypeAndStatus(
                        anyLong(), anyLong(), any(), eq(AllocationStatus.RECOMMENDED)))
                .thenReturn(Optional.empty());

        generation.generate(20L);

        ArgumentCaptor<AllocationDecision> captor = ArgumentCaptor.forClass(AllocationDecision.class);
        verify(allocations, times(1)).save(captor.capture());
        AllocationDecision saved = captor.getValue();
        assertThat(saved.getSourceCampId()).isEqualTo(3L); // the larger surplus, not the weaker one
        assertThat(saved.getTargetCampId()).isEqualTo(1L);
        assertThat(saved.getResourceType()).isEqualTo("WATER");
        assertThat(saved.getRecommendedQuantity()).isEqualByComparingTo(BigDecimal.valueOf(30)); // min(40, 30)
        assertThat(saved.getSourceCampId()).isNotEqualTo(saved.getTargetCampId());
        assertThat(saved.getRecommendedQuantity().signum()).isPositive();
    }

    private double anyDouble() {
        return org.mockito.ArgumentMatchers.anyDouble();
    }

    @Test
    void refreshesAnExistingRecommendationInPlaceInsteadOfSavingADuplicate() {
        Camp shortageCamp = camp(1L, "shortage", 100);
        Camp surplusCamp = camp(2L, "surplus", 100);
        when(camps.findAll()).thenReturn(List.of(shortageCamp, surplusCamp));

        stubAllResourceTypesNoGapNoSurplus(1L);
        stubAllResourceTypesNoGapNoSurplus(2L);
        when(scoring.resourceState(1L, "FOOD", 20L)).thenReturn(new AllocationScoringService.CampResourceState(
                1L, "FOOD", BigDecimal.valueOf(10), BigDecimal.valueOf(50), BigDecimal.valueOf(40), BigDecimal.ZERO, 3L));
        when(scoring.resourceState(2L, "FOOD", 20L)).thenReturn(new AllocationScoringService.CampResourceState(
                2L, "FOOD", BigDecimal.valueOf(80), BigDecimal.valueOf(50), BigDecimal.ZERO, BigDecimal.valueOf(30), 20L));

        when(scoring.severityScore(1L)).thenReturn(0.2);
        when(scoring.shortageScore(3L)).thenReturn(0.9);
        when(scoring.ticksSinceLastApproved(1L, "FOOD", 20L)).thenReturn(5L);
        when(scoring.priorityScore(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(0.5);

        AllocationDecision existing = new AllocationDecision(
                "FOOD", 2L, 1L, BigDecimal.valueOf(15), 0.1, 0.1, 0.1, 0.1, 0.1, 5);
        when(allocations.findBySourceCampIdAndTargetCampIdAndResourceTypeAndStatus(
                        2L, 1L, "FOOD", AllocationStatus.RECOMMENDED))
                .thenReturn(Optional.of(existing));

        generation.generate(20L);

        verify(allocations, never()).save(any());
        assertThat(existing.getRecommendedQuantity()).isEqualByComparingTo(BigDecimal.valueOf(30)); // min(40,30)
        assertThat(existing.getStatus()).isEqualTo(AllocationStatus.RECOMMENDED);
        assertThat(existing.getGeneratedAtTick()).isEqualTo(20L);
    }

    @Test
    void aShortageWithNoSurplusAnywhereIsSkippedEntirely() {
        Camp shortageCamp = camp(1L, "shortage", 100);
        Camp alsoShortCamp = camp(2L, "also-short", 100);
        when(camps.findAll()).thenReturn(List.of(shortageCamp, alsoShortCamp));

        for (long campId : List.of(1L, 2L)) {
            for (String rt : List.of("WATER", "FOOD", "MEDICAL")) {
                when(scoring.resourceState(campId, rt, 20L)).thenReturn(
                        new AllocationScoringService.CampResourceState(
                                campId, rt, BigDecimal.valueOf(5), BigDecimal.valueOf(50),
                                BigDecimal.valueOf(45), BigDecimal.ZERO, 1L));
            }
        }

        generation.generate(20L);

        verify(allocations, never()).save(any());
        verify(allocations, never()).findBySourceCampIdAndTargetCampIdAndResourceTypeAndStatus(
                any(), any(), any(), any());
    }
}
