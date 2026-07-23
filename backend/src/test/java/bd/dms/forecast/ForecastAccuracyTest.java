package bd.dms.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

import bd.dms.sim.Scenario;
import java.math.BigDecimal;
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
 * Stands in for ticket 14's not-yet-built full validation harness: drives the pure
 * {@link Scenario#stateAt(long)} through the whole scripted run, feeds a real
 * {@link ForecastService} on an in-memory observation store built the same way
 * {@code SimulationEngine} builds it, and reports one-step-ahead forecast MAE grouped by each
 * camp/resource's scripted {@link Scenario.DataQualityCondition}. Asserts degraded-quality
 * combos are measurably worse than normal ones — the whole point of a confidence band.
 *
 * <p>The repository fake is a Mockito {@code @Mock} with a stateful {@code Answer} backed by an
 * in-memory list (mirrors {@link ForecastServiceTest}'s style) rather than a hand-rolled
 * {@code JpaRepository} implementation — {@link CampResourceObservationRepository} pulls in
 * dozens of inherited methods this test never calls, so a full hand implementation would be
 * mostly dead code. This was tried first per the brief's preference and worked cleanly; no need
 * to fall back to narrowing {@code ForecastService}'s dependency to a smaller interface.
 */
@ExtendWith(MockitoExtension.class)
class ForecastAccuracyTest {

    private static final List<String> RESOURCE_TYPES = List.of("WATER", "FOOD", "MEDICAL");

    @Mock
    private CampResourceObservationRepository observations;

    private final List<CampResourceObservation> rows = new ArrayList<>();

    private ForecastService forecastService;

    @BeforeEach
    void setUp() {
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
        forecastService = new ForecastService(observations);
    }

    @Test
    void degradedDataQualityComboHasHigherMaeThanNormalCombos() {
        Map<Scenario.DataQualityCondition, List<Double>> errorsByCondition = new HashMap<>();
        for (Scenario.DataQualityCondition c : Scenario.DataQualityCondition.values()) {
            errorsByCondition.put(c, new ArrayList<>());
        }

        for (long tick = 0; tick < Scenario.LENGTH; tick++) {
            var state = Scenario.stateAt(tick);
            var nextState = Scenario.stateAt(tick + 1);
            for (String campCode : state.camps().keySet()) {
                Long campId = idFor(campCode);
                for (String resourceType : RESOURCE_TYPES) {
                    BigDecimal qty = state.camp(campCode).resources().get(resourceType);
                    if (qty == null) {
                        continue;
                    }
                    if (Scenario.shouldRecordObservation(campCode, resourceType, tick)) {
                        record(campId, resourceType, qty, tick);
                        if (Scenario.dataQualityCondition(campCode, resourceType)
                                == Scenario.DataQualityCondition.CONFLICTING_PRONE) {
                            record(campId, resourceType, qty.multiply(BigDecimal.valueOf(1.2)), tick);
                        }
                    }
                    if (tick < 2) {
                        continue; // not enough history yet for a meaningful rate
                    }
                    ForecastResult forecast = forecastService.forecast(campId, resourceType, tick);
                    BigDecimal predictedNext = forecast.currentQuantity().subtract(forecast.ratePerTick());
                    BigDecimal actualNext = nextState.camp(campCode).resources().get(resourceType);
                    double error = Math.abs(predictedNext.doubleValue() - actualNext.doubleValue());
                    errorsByCondition.get(Scenario.dataQualityCondition(campCode, resourceType)).add(error);
                }
            }
        }

        double normalMae = mean(errorsByCondition.get(Scenario.DataQualityCondition.NORMAL));
        double staleMae = mean(errorsByCondition.get(Scenario.DataQualityCondition.STALE_PRONE));
        double conflictingMae = mean(errorsByCondition.get(Scenario.DataQualityCondition.CONFLICTING_PRONE));

        assertThat(normalMae).isFinite();
        assertThat(staleMae).isGreaterThan(normalMae);
        assertThat(conflictingMae).isGreaterThan(normalMae);
    }

    private void record(Long campId, String resourceType, BigDecimal quantity, long tick) {
        rows.add(new CampResourceObservation(campId, resourceType, quantity, tick));
    }

    private double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    // Deterministic small ids — the forecaster never touches a real Camp row in this test.
    private Long idFor(String campCode) {
        return (long) campCode.hashCode();
    }
}
