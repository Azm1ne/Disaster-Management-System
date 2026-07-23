package bd.dms.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock
    private CampResourceObservationRepository observations;

    private ForecastService forecastService;

    @BeforeEach
    void setUp() {
        forecastService = new ForecastService(observations);
    }

    private CampResourceObservation obs(long tick, double qty) {
        return new CampResourceObservation(1L, "WATER", BigDecimal.valueOf(qty), tick);
    }

    @Test
    void steadyDepletionYieldsAPositiveRateAndAHighConfidenceNarrowBand() {
        when(observations.findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(1L, "WATER", 0))
                .thenReturn(List.of(obs(0, 100), obs(1, 90), obs(2, 80), obs(3, 70)));

        ForecastResult result = forecastService.forecast(1L, "WATER", 3);

        assertThat(result.ratePerTick()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(result.ticksRemainingEstimate()).isEqualTo(7L); // 70 / 10
        assertThat(result.confidenceLevel()).isEqualTo("HIGH");
        long bandWidth = result.ticksRemainingBestCase() - result.ticksRemainingWorstCase();
        assertThat(bandWidth).isLessThan(3);
    }

    @Test
    void noDepletionYieldsNoExhaustionHorizon() {
        when(observations.findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(1L, "WATER", 0))
                .thenReturn(List.of(obs(0, 100), obs(1, 100), obs(2, 100)));

        ForecastResult result = forecastService.forecast(1L, "WATER", 2);

        assertThat(result.ratePerTick()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.ticksRemainingEstimate()).isNull();
    }

    @Test
    void staleHistoryLowersConfidenceAndWidensTheBandVersusFreshHistory() {
        when(observations.findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(1L, "WATER", 0))
                .thenReturn(List.of(obs(0, 100), obs(1, 90)));

        ForecastResult fresh = forecastService.forecast(1L, "WATER", 1);
        ForecastResult stale = forecastService.forecast(1L, "WATER", 6); // 5-tick-old latest reading

        assertThat(stale.confidenceScore()).isLessThan(fresh.confidenceScore());
        long freshBand = fresh.ticksRemainingBestCase() - fresh.ticksRemainingWorstCase();
        long staleBand = stale.ticksRemainingBestCase() - stale.ticksRemainingWorstCase();
        assertThat(staleBand).isGreaterThan(freshBand);
    }

    @Test
    void conflictingReadingsForTheSameTickLowerConfidenceVersusAgreeingReadings() {
        when(observations.findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(1L, "FOOD", 0))
                .thenReturn(List.of(obs(0, 100), obs(1, 90), obs(1, 60))); // disagreeing tick-1 readings
        when(observations.findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(1L, "WATER", 0))
                .thenReturn(List.of(obs(0, 100), obs(1, 90)));

        ForecastResult conflicting = forecastService.forecast(1L, "FOOD", 1);
        ForecastResult agreeing = forecastService.forecast(1L, "WATER", 1);

        assertThat(conflicting.confidenceScore()).isLessThan(agreeing.confidenceScore());
    }

    @Test
    void noObservationsYieldsZeroConfidenceAndNoEstimate() {
        when(observations.findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(1L, "WATER", 0))
                .thenReturn(List.of());

        ForecastResult result = forecastService.forecast(1L, "WATER", 5);

        assertThat(result.confidenceScore()).isEqualTo(0.0);
        assertThat(result.ticksRemainingEstimate()).isNull();
        assertThat(result.confidenceLevel()).isEqualTo("LOW");
    }
}
