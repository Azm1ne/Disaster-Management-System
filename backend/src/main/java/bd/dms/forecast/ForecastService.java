package bd.dms.forecast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Stateless: recomputes a forecast fresh from {@link CampResourceObservationRepository} history
 * every call, no cached forecast row. The rate is an exponentially-weighted average of per-tick
 * consumption over a lookback window, so recent ticks dominate stale ones. Confidence starts at
 * 1.0 and is reduced by (a) disagreement between multiple readings sharing the same tick
 * ("conflicting") and (b) how many ticks old the latest reading is versus the tick the forecast
 * is computed for ("stale") — both only ever narrow the band's trust, never widen it.
 */
@Service
public class ForecastService {

    private static final long LOOKBACK_TICKS = 10;
    private static final double DECAY = 0.8;
    private static final long STALE_FULL_PENALTY_TICKS = 5;

    private final CampResourceObservationRepository observations;

    public ForecastService(CampResourceObservationRepository observations) {
        this.observations = observations;
    }

    public ForecastResult forecast(Long campId, String resourceType, long currentTick) {
        long fromTick = Math.max(0, currentTick - LOOKBACK_TICKS);
        List<CampResourceObservation> rows = observations
                .findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(campId, resourceType, fromTick);

        if (rows.isEmpty()) {
            return new ForecastResult(campId, resourceType, BigDecimal.ZERO, BigDecimal.ZERO,
                    null, null, null, 0.0, "LOW", -1, 0);
        }

        // Group same-tick readings; disagreement between them is the "conflicting" signal.
        Map<Long, List<BigDecimal>> byTick = new LinkedHashMap<>();
        for (CampResourceObservation row : rows) {
            byTick.computeIfAbsent(row.getTick(), t -> new ArrayList<>()).add(row.getQuantity());
        }
        List<Long> ticks = new ArrayList<>(byTick.keySet());
        long latestObservedTick = ticks.get(ticks.size() - 1);

        List<BigDecimal> representative = new ArrayList<>();
        double conflictPenalty = 0.0;
        int conflictingGroups = 0;
        for (Long t : ticks) {
            List<BigDecimal> values = byTick.get(t);
            BigDecimal mean = average(values);
            representative.add(mean);
            if (values.size() > 1) {
                conflictingGroups++;
                conflictPenalty += coefficientOfVariation(values, mean);
            }
        }
        if (conflictingGroups > 0) {
            conflictPenalty = Math.min(1.0, conflictPenalty / conflictingGroups);
        }

        // Weighted rate: consumption between consecutive representative points, decayed by age.
        double weightedNumerator = 0.0;
        double weightedDenominator = 0.0;
        for (int i = 1; i < ticks.size(); i++) {
            long dt = ticks.get(i) - ticks.get(i - 1);
            if (dt <= 0) {
                continue;
            }
            double delta = Math.max(0.0, representative.get(i - 1).doubleValue() - representative.get(i).doubleValue());
            double perTick = delta / dt;
            double weight = Math.pow(DECAY, currentTick - ticks.get(i));
            weightedNumerator += weight * perTick;
            weightedDenominator += weight;
        }
        double rate = weightedDenominator > 0 ? weightedNumerator / weightedDenominator : 0.0;
        BigDecimal currentQuantity = representative.get(representative.size() - 1);

        long recencyGapTicks = Math.max(0, currentTick - latestObservedTick);
        double stalePenalty = Math.min(1.0, (double) recencyGapTicks / STALE_FULL_PENALTY_TICKS);
        double confidenceScore = Math.max(0.0, 1.0 - conflictPenalty - stalePenalty);
        String confidenceLevel = confidenceScore >= 0.7 ? "HIGH" : confidenceScore >= 0.4 ? "MEDIUM" : "LOW";

        if (rate <= 0.0) {
            return new ForecastResult(campId, resourceType, currentQuantity, BigDecimal.ZERO,
                    null, null, null, confidenceScore, confidenceLevel, latestObservedTick, ticks.size());
        }

        double ticksRemaining = currentQuantity.doubleValue() / rate;
        double bandFraction = 1.0 - confidenceScore; // 0 (perfect trust) .. 1 (no trust)
        long worstCase = Math.round(Math.max(0.0, ticksRemaining * (1.0 - bandFraction)));
        long bestCase = Math.round(ticksRemaining * (1.0 + bandFraction));

        return new ForecastResult(
                campId, resourceType, currentQuantity,
                BigDecimal.valueOf(rate).setScale(2, RoundingMode.HALF_UP),
                Math.round(ticksRemaining), worstCase, bestCase,
                confidenceScore, confidenceLevel, latestObservedTick, ticks.size());
    }

    private BigDecimal average(List<BigDecimal> values) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            sum = sum.add(v);
        }
        return sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private double coefficientOfVariation(List<BigDecimal> values, BigDecimal mean) {
        if (mean.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        double variance = 0.0;
        for (BigDecimal v : values) {
            double diff = v.doubleValue() - mean.doubleValue();
            variance += diff * diff;
        }
        variance /= values.size();
        double stdev = Math.sqrt(variance);
        return Math.min(1.0, stdev / mean.doubleValue());
    }
}
