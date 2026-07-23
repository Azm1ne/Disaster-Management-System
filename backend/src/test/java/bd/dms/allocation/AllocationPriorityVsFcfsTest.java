package bd.dms.allocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import bd.dms.family.FamilyMemberRepository;
import bd.dms.forecast.ForecastResult;
import bd.dms.forecast.ForecastService;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Stands in for ticket 14's not-yet-built full validation harness: constructs a small, fixed
 * three-camp scenario (one severe shortage camp, one mild shortage camp, one surplus camp with
 * enough stock to serve only one of them fully) and compares two allocation strategies over the
 * same fixed surplus pool — priority-ranked (by {@link AllocationScoringService#priorityScore})
 * versus naive FCFS (camp id order) — reporting each strategy's total unmet
 * medical-severity-weighted shortage. Asserts priority strictly beats FCFS in this constructed
 * scenario, where the high-severity camp is deliberately given a *later* camp id than the
 * low-severity camp so FCFS would serve the wrong one first.
 */
@ExtendWith(MockitoExtension.class)
class AllocationPriorityVsFcfsTest {

    private static final long TICK = 20;

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
        lenient()
                .when(allocations.findByTargetCampIdAndResourceTypeAndStatusIn(
                        anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
    }

    private void stubForecast(long campId, BigDecimal currentQuantity, BigDecimal ratePerTick) {
        when(forecastService.forecast(campId, "WATER", TICK)).thenReturn(new ForecastResult(
                campId, "WATER", currentQuantity, ratePerTick, null, null, null, 1.0, "HIGH", TICK, 5));
    }

    private void stubSeverity(long campId, long medical, long total) {
        when(familyMembers.countByCampId(campId)).thenReturn(total);
        when(familyMembers.countMedicalFlagTrueByCampId(campId)).thenReturn(medical);
    }

    @Test
    void priorityOrderingLeavesLessUnmetSeverityWeightedShortageThanFcfs() {
        // Camp 1: severe shortage, high medical severity, but a HIGHER camp id than camp 2 — FCFS
        // (camp-id order) would serve camp 2 first and starve camp 1 if the pooled surplus can't
        // cover both.
        long lowSeverityCamp = 1L;
        long highSeverityCamp = 2L;
        long surplusCamp = 3L;

        stubForecast(lowSeverityCamp, BigDecimal.valueOf(10), BigDecimal.valueOf(3)); // gap = 20 (30-10)
        stubForecast(highSeverityCamp, BigDecimal.valueOf(10), BigDecimal.valueOf(3)); // gap = 20
        stubForecast(surplusCamp, BigDecimal.valueOf(35), BigDecimal.valueOf(1)); // reserve=10, surplus=25

        stubSeverity(lowSeverityCamp, 0, 10); // 0.0
        stubSeverity(highSeverityCamp, 9, 10); // 0.9

        double lowGap = scoring.resourceState(lowSeverityCamp, "WATER", TICK).gap().doubleValue(); // 20
        double highGap = scoring.resourceState(highSeverityCamp, "WATER", TICK).gap().doubleValue(); // 20
        // Pooled surplus (derived the same way, from the surplus camp) can serve only one camp's
        // full 20-unit gap plus a little of the other's.
        double poolSize = scoring.resourceState(surplusCamp, "WATER", TICK).surplus().doubleValue(); // 25
        double lowSeverity = scoring.severityScore(lowSeverityCamp);
        double highSeverity = scoring.severityScore(highSeverityCamp);
        double lowPriority = scoring.priorityScore(lowSeverity, 0.0, 0.0, 0.0);
        double highPriority = scoring.priorityScore(highSeverity, 0.0, 0.0, 0.0);

        double fcfsUnmetSeverityWeighted = fulfillInOrder(
                List.of(
                        new Camp(lowSeverityCamp, lowGap, lowSeverity),
                        new Camp(highSeverityCamp, highGap, highSeverity)),
                poolSize);

        List<Camp> priorityOrder = List.of(
                        new Camp(lowSeverityCamp, lowGap, lowSeverity),
                        new Camp(highSeverityCamp, highGap, highSeverity))
                .stream()
                .sorted(Comparator.comparingDouble(
                                (Camp camp) -> scoring.priorityScore(camp.severity(), 0.0, 0.0, 0.0))
                        .reversed())
                .toList();

        double priorityUnmetSeverityWeighted = fulfillInOrder(priorityOrder, poolSize);

        System.out.printf(
                "priority-vs-FCFS: FCFS unmet severity-weighted shortage=%.2f, priority unmet=%.2f%n",
                fcfsUnmetSeverityWeighted, priorityUnmetSeverityWeighted);
        assertThat(highPriority).isGreaterThan(lowPriority);
        assertThat(priorityUnmetSeverityWeighted).isLessThan(fcfsUnmetSeverityWeighted);
    }

    private record Camp(long id, double gap, double severity) {}

    /** Serves camps in list order from a shared pool until it runs out, then reports the sum of
     * (unmet gap * severity) across every camp — the harm metric this scenario is built to show
     * priority ordering reduces. */
    private double fulfillInOrder(List<Camp> campsInServiceOrder, double poolSize) {
        double remainingPool = poolSize;
        double unmetSeverityWeighted = 0.0;
        for (Camp camp : campsInServiceOrder) {
            double served = Math.min(camp.gap(), remainingPool);
            remainingPool -= served;
            double unmet = camp.gap() - served;
            unmetSeverityWeighted += unmet * camp.severity();
        }
        return unmetSeverityWeighted;
    }
}
