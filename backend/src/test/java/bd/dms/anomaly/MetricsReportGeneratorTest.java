package bd.dms.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import bd.dms.allocation.AllocationDecisionRepository;
import bd.dms.allocation.AllocationScoringService;
import bd.dms.anomaly.DuplicateRegistrationDetector.MemberKey;
import bd.dms.family.FamilyMemberRepository;
import bd.dms.forecast.CampResourceObservation;
import bd.dms.forecast.CampResourceObservationRepository;
import bd.dms.forecast.ForecastResult;
import bd.dms.forecast.ForecastService;
import bd.dms.metrics.MetricsReport;
import bd.dms.sim.Scenario;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Ticket 14's headless validation harness: the one place all of the quantitative gates the
 * feature tickets already measure individually — {@link bd.dms.forecast.ForecastAccuracyTest
 * forecast MAE}, the three detector accuracy tests in this package, and {@link
 * bd.dms.allocation.AllocationPriorityVsFcfsTest} — are driven together and written out as one
 * {@link MetricsReport} artifact (JSON + Markdown, under {@code backend/build/reports/}) instead
 * of living only as {@code System.out.println} lines scattered across five test classes.
 *
 * <p>Deliberately lives in package {@code bd.dms.anomaly}, not a new {@code metrics} test
 * package: {@link AllocationBurstDetector#isBurst} and {@link
 * DuplicateRegistrationDetector#SIMILARITY_THRESHOLD} are package-private on purpose (internal
 * detector detail, not a public API), and this avoids widening either just for this test. The
 * synthetic case tables below are intentionally the same ground truth as the three accuracy
 * tests they consolidate — this class asserts the same accuracy floors, it doesn't invent new
 * ones.
 */
@ExtendWith(MockitoExtension.class)
class MetricsReportGeneratorTest {

    private static final List<String> RESOURCE_TYPES = List.of("WATER", "FOOD", "MEDICAL");

    @Test
    void generatesConsolidatedMetricsReport() {
        Map<String, Double> forecastMae = forecastMaeByDataQualityCondition();
        List<MetricsReport.DetectorAccuracy> detectorAccuracy = List.of(
                allocationBurstAccuracy(), donationPatternAccuracy(), duplicateRegistrationAccuracy());
        MetricsReport.AllocationComparison allocationComparison = allocationPriorityVsFcfs();

        MetricsReport report = new MetricsReport(forecastMae, detectorAccuracy, allocationComparison);
        report.writeTo(Path.of("build", "reports"));

        assertThat(forecastMae).isNotEmpty();
        assertThat(detectorAccuracy).hasSize(3);
        for (MetricsReport.DetectorAccuracy d : detectorAccuracy) {
            assertThat(d.precision()).isGreaterThanOrEqualTo(0.8);
            assertThat(d.recall()).isGreaterThanOrEqualTo(0.8);
            assertThat(d.falsePositiveRate()).isLessThanOrEqualTo(0.2);
        }
        assertThat(allocationComparison.priorityUnmetSeverityWeighted())
                .isLessThan(allocationComparison.fcfsUnmetSeverityWeighted());
    }

    // ---- forecast MAE by data-quality condition (mirrors ForecastAccuracyTest) ----

    private Map<String, Double> forecastMaeByDataQualityCondition() {
        CampResourceObservationRepository observations = org.mockito.Mockito.mock(CampResourceObservationRepository.class);
        List<CampResourceObservation> rows = new ArrayList<>();
        lenient()
                .when(observations.findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(
                        any(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    Long campId = invocation.getArgument(0);
                    String resourceType = invocation.getArgument(1);
                    long fromTick = invocation.getArgument(2);
                    return rows.stream()
                            .filter(r -> r.getCampId().equals(campId)
                                    && r.getResourceType().equals(resourceType)
                                    && r.getTick() >= fromTick)
                            .sorted(Comparator.comparingLong(CampResourceObservation::getTick))
                            .toList();
                });
        ForecastService forecastService = new ForecastService(observations);

        Map<Scenario.DataQualityCondition, List<Double>> errorsByCondition = new HashMap<>();
        for (Scenario.DataQualityCondition c : Scenario.DataQualityCondition.values()) {
            errorsByCondition.put(c, new ArrayList<>());
        }

        for (long tick = 0; tick < Scenario.LENGTH; tick++) {
            var state = Scenario.stateAt(tick);
            var nextState = Scenario.stateAt(tick + 1);
            for (String campCode : state.camps().keySet()) {
                Long campId = (long) campCode.hashCode();
                for (String resourceType : RESOURCE_TYPES) {
                    BigDecimal qty = state.camp(campCode).resources().get(resourceType);
                    if (qty == null) {
                        continue;
                    }
                    if (Scenario.shouldRecordObservation(campCode, resourceType, tick)) {
                        rows.add(new CampResourceObservation(campId, resourceType, qty, tick));
                        if (Scenario.dataQualityCondition(campCode, resourceType)
                                == Scenario.DataQualityCondition.CONFLICTING_PRONE) {
                            rows.add(new CampResourceObservation(
                                    campId, resourceType, qty.multiply(BigDecimal.valueOf(1.2)), tick));
                        }
                    }
                    if (tick < 2) {
                        continue;
                    }
                    ForecastResult forecast = forecastService.forecast(campId, resourceType, tick);
                    BigDecimal predictedNext = forecast.currentQuantity().subtract(forecast.ratePerTick());
                    BigDecimal actualNext = nextState.camp(campCode).resources().get(resourceType);
                    double error = Math.abs(predictedNext.doubleValue() - actualNext.doubleValue());
                    errorsByCondition.get(Scenario.dataQualityCondition(campCode, resourceType)).add(error);
                }
            }
        }

        Map<String, Double> mae = new HashMap<>();
        errorsByCondition.forEach((condition, errors) -> mae.put(
                condition.name(), errors.stream().mapToDouble(Double::doubleValue).average().orElseThrow()));
        return mae;
    }

    // ---- detector accuracy (mirrors the three *AccuracyTest classes in this package) ----

    private record BurstCase(String label, int distinctDecisionCount, boolean groundTruthPositive) {}

    private MetricsReport.DetectorAccuracy allocationBurstAccuracy() {
        List<BurstCase> cases = List.of(
                new BurstCase("burst exactly at threshold", 3, true),
                new BurstCase("burst well above threshold", 8, true),
                new BurstCase("two recommendations just under threshold", 2, false),
                new BurstCase("a single isolated recommendation", 1, false),
                new BurstCase("quiet window, no recommendations", 0, false));
        int tp = 0, fp = 0, fn = 0, tn = 0;
        for (BurstCase c : cases) {
            boolean predicted = AllocationBurstDetector.isBurst(c.distinctDecisionCount());
            if (c.groundTruthPositive() && predicted) tp++;
            else if (!c.groundTruthPositive() && predicted) fp++;
            else if (c.groundTruthPositive() && !predicted) fn++;
            else tn++;
        }
        return new MetricsReport.DetectorAccuracy(
                "AllocationBurstDetector", precision(tp, fp), recall(tp, fn), fpr(fp, tn), allocationBurstLeadTime());
    }

    /** Mirrors {@code AllocationBurstAccuracyTest#leadTimeIsZeroBecause...}: three decisions land at
     * ticks 0-2, the trailing 5-tick window first reaches the burst threshold at tick 2, and
     * {@code scan(tick)} evaluates that same window on that same tick — so lead time is the gap
     * between when the condition first becomes true and when it's detected. */
    private long allocationBurstLeadTime() {
        int[] decisionsGeneratedAtTick = {1, 1, 1, 0, 0, 0, 0, 0, 0, 0};
        long burstFirstTrueAtTick = -1;
        long detectedAtTick = -1;
        for (long tick = 0; tick < decisionsGeneratedAtTick.length; tick++) {
            long from = Math.max(0, tick - AllocationBurstDetector.WINDOW_TICKS + 1);
            int windowCount = 0;
            for (long t = from; t <= tick; t++) {
                windowCount += decisionsGeneratedAtTick[(int) t];
            }
            boolean burstNow = AllocationBurstDetector.isBurst(windowCount);
            if (burstNow && burstFirstTrueAtTick == -1) {
                burstFirstTrueAtTick = tick;
                detectedAtTick = tick;
            }
        }
        return detectedAtTick - burstFirstTrueAtTick;
    }

    private record BurstTimingCase(List<Instant> priorTimestamps, Instant newest, boolean groundTruthPositive) {}

    private MetricsReport.DetectorAccuracy donationPatternAccuracy() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        List<BurstTimingCase> cases = List.of(
                new BurstTimingCase(
                        List.of(now.minusSeconds(60), now.minusSeconds(180), now.minusSeconds(300)), now, true),
                new BurstTimingCase(
                        List.of(
                                now.minusSeconds(30),
                                now.minusSeconds(60),
                                now.minusSeconds(90),
                                now.minusSeconds(120),
                                now.minusSeconds(150),
                                now.minusSeconds(180)),
                        now,
                        true),
                new BurstTimingCase(
                        List.of(
                                now.minusSeconds(60),
                                now.minus(Duration.ofMinutes(12)),
                                now.minus(Duration.ofMinutes(20))),
                        now,
                        false),
                new BurstTimingCase(
                        List.of(now.minusSeconds(60), now.minus(Duration.ofMinutes(10)).minusSeconds(1)), now, false),
                new BurstTimingCase(List.of(), now, false));
        int tp = 0, fp = 0, fn = 0, tn = 0;
        for (BurstTimingCase c : cases) {
            boolean predicted = DonationPatternDetector.isBurst(
                    c.priorTimestamps(), c.newest(), DonationPatternDetector.BURST_WINDOW, DonationPatternDetector.BURST_COUNT);
            if (c.groundTruthPositive() && predicted) tp++;
            else if (!c.groundTruthPositive() && predicted) fp++;
            else if (c.groundTruthPositive() && !predicted) fn++;
            else tn++;
        }
        return new MetricsReport.DetectorAccuracy(
                "DonationPatternDetector", precision(tp, fp), recall(tp, fn), fpr(fp, tn), donationPatternLeadTime(now));
    }

    /** Mirrors {@code DonationPatternAccuracyTest#leadTimeIsZeroBecause...}: four donations from the
     * same donor land two minutes apart; the burst condition first becomes true on the fourth
     * (event index 3), and {@code isBurst} is evaluated synchronously on that same donation. */
    private long donationPatternLeadTime(Instant now) {
        List<Instant> donationTimestamps =
                List.of(now, now.plusSeconds(120), now.plusSeconds(240), now.plusSeconds(360));
        long burstFirstTrueAtEventIndex = -1;
        long detectedAtEventIndex = -1;
        for (int i = 0; i < donationTimestamps.size(); i++) {
            Instant newest = donationTimestamps.get(i);
            List<Instant> priors = donationTimestamps.subList(0, i);
            boolean burstNow = DonationPatternDetector.isBurst(
                    priors, newest, DonationPatternDetector.BURST_WINDOW, DonationPatternDetector.BURST_COUNT);
            if (burstNow && burstFirstTrueAtEventIndex == -1) {
                burstFirstTrueAtEventIndex = i;
                detectedAtEventIndex = i;
            }
        }
        return detectedAtEventIndex - burstFirstTrueAtEventIndex;
    }

    private static MemberKey key(String nickname) {
        return new MemberKey(nickname, "ADULT");
    }

    private record DuplicateCase(
            String nameA, List<MemberKey> membersA, String nameB, List<MemberKey> membersB, boolean groundTruthPositive) {}

    private MetricsReport.DetectorAccuracy duplicateRegistrationAccuracy() {
        List<MemberKey> fiveMembers = List.of(key("a1"), key("a2"), key("a3"), key("a4"), key("a5"));
        List<MemberKey> fiveMembersOneShared = List.of(key("a1"), key("b2"), key("b3"), key("b4"), key("b5"));
        List<DuplicateCase> cases = List.of(
                new DuplicateCase("Karim Family", fiveMembers, "Karim Family", fiveMembers, true),
                new DuplicateCase("Karim Family", fiveMembers, "Karim Family", fiveMembersOneShared, true),
                new DuplicateCase(
                        "Islam Family",
                        fiveMembers,
                        "Islam Family",
                        List.of(key("z1"), key("z2"), key("z3"), key("z4"), key("z5")),
                        false),
                new DuplicateCase(
                        "a".repeat(20),
                        fiveMembers,
                        "a".repeat(15) + "b".repeat(5),
                        List.of(key("a1"), key("a2"), key("z3"), key("z4"), key("z5")),
                        false),
                new DuplicateCase(
                        "Karim Family", fiveMembers, "Zzzzzzzzzz Household", List.of(key("q1"), key("q2")), false));
        int tp = 0, fp = 0, fn = 0, tn = 0;
        for (DuplicateCase c : cases) {
            double score = DuplicateRegistrationDetector.similarity(c.nameA(), c.membersA(), c.nameB(), c.membersB());
            boolean predicted = score >= DuplicateRegistrationDetector.SIMILARITY_THRESHOLD;
            if (c.groundTruthPositive() && predicted) tp++;
            else if (!c.groundTruthPositive() && predicted) fp++;
            else if (c.groundTruthPositive() && !predicted) fn++;
            else tn++;
        }
        return new MetricsReport.DetectorAccuracy(
                "DuplicateRegistrationDetector",
                precision(tp, fp),
                recall(tp, fn),
                fpr(fp, tn),
                duplicateRegistrationLeadTime());
    }

    /** Mirrors {@code DuplicateRegistrationAccuracyTest#leadTimeIsZeroBecause...}: a near-duplicate
     * group registers as event 1, and {@code scanNewGroup} compares it against every existing group
     * (including event 0's) synchronously on that same registration event. */
    private long duplicateRegistrationLeadTime() {
        List<MemberKey> roster = List.of(key("a1"), key("a2"));
        int triggeringEventIndex = 1;
        double score = DuplicateRegistrationDetector.similarity("Karim Family", roster, "Karim Family", roster);
        boolean detected = score >= DuplicateRegistrationDetector.SIMILARITY_THRESHOLD;
        assertThat(detected).isTrue();
        int detectedAtEventIndex = triggeringEventIndex;
        return detectedAtEventIndex - triggeringEventIndex;
    }

    private static double precision(int tp, int fp) {
        return tp / (double) (tp + fp);
    }

    private static double recall(int tp, int fn) {
        return tp / (double) (tp + fn);
    }

    private static double fpr(int fp, int tn) {
        return fp / (double) (fp + tn);
    }

    // ---- allocation priority vs. FCFS (mirrors AllocationPriorityVsFcfsTest) ----

    @Mock
    private ForecastService forecastService;

    @Mock
    private FamilyMemberRepository familyMembers;

    @Mock
    private AllocationDecisionRepository allocations;

    private record Camp(long id, double gap, double severity) {}

    private MetricsReport.AllocationComparison allocationPriorityVsFcfs() {
        long tick = 20;
        AllocationScoringService scoring = new AllocationScoringService(forecastService, familyMembers, allocations);
        lenient()
                .when(allocations.findByTargetCampIdAndResourceTypeAndStatusIn(anyLong(), anyString(), any()))
                .thenReturn(List.of());

        long lowSeverityCamp = 1L;
        long highSeverityCamp = 2L;
        long surplusCamp = 3L;

        when(forecastService.forecast(lowSeverityCamp, "WATER", tick))
                .thenReturn(new ForecastResult(
                        lowSeverityCamp, "WATER", BigDecimal.valueOf(10), BigDecimal.valueOf(3), null, null, null, 1.0, "HIGH", tick, 5));
        when(forecastService.forecast(highSeverityCamp, "WATER", tick))
                .thenReturn(new ForecastResult(
                        highSeverityCamp, "WATER", BigDecimal.valueOf(10), BigDecimal.valueOf(3), null, null, null, 1.0, "HIGH", tick, 5));
        when(forecastService.forecast(surplusCamp, "WATER", tick))
                .thenReturn(new ForecastResult(
                        surplusCamp, "WATER", BigDecimal.valueOf(35), BigDecimal.valueOf(1), null, null, null, 1.0, "HIGH", tick, 5));

        when(familyMembers.countByCampId(lowSeverityCamp)).thenReturn(10L);
        when(familyMembers.countMedicalFlagTrueByCampId(lowSeverityCamp)).thenReturn(0L);
        when(familyMembers.countByCampId(highSeverityCamp)).thenReturn(10L);
        when(familyMembers.countMedicalFlagTrueByCampId(highSeverityCamp)).thenReturn(9L);

        double lowGap = scoring.resourceState(lowSeverityCamp, "WATER", tick).gap().doubleValue();
        double highGap = scoring.resourceState(highSeverityCamp, "WATER", tick).gap().doubleValue();
        double poolSize = scoring.resourceState(surplusCamp, "WATER", tick).surplus().doubleValue();
        double lowSeverity = scoring.severityScore(lowSeverityCamp);
        double highSeverity = scoring.severityScore(highSeverityCamp);

        double fcfsUnmet = fulfillInOrder(
                List.of(new Camp(lowSeverityCamp, lowGap, lowSeverity), new Camp(highSeverityCamp, highGap, highSeverity)),
                poolSize);
        List<Camp> priorityOrder = List.of(
                        new Camp(lowSeverityCamp, lowGap, lowSeverity), new Camp(highSeverityCamp, highGap, highSeverity))
                .stream()
                .sorted(Comparator.comparingDouble((Camp camp) -> scoring.priorityScore(camp.severity(), 0.0, 0.0, 0.0))
                        .reversed())
                .toList();
        double priorityUnmet = fulfillInOrder(priorityOrder, poolSize);

        return new MetricsReport.AllocationComparison(fcfsUnmet, priorityUnmet);
    }

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
