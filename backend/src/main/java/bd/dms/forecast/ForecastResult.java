package bd.dms.forecast;

import java.math.BigDecimal;

/**
 * One camp/resource's explainable forecast at the tick it was computed. {@code ticksRemaining*}
 * are null when the weighted rate isn't positive (nothing to exhaust). Worst/best case bracket
 * the point estimate — the band narrows as {@code confidenceScore} rises toward 1.0.
 */
public record ForecastResult(
        Long campId,
        String resourceType,
        BigDecimal currentQuantity,
        BigDecimal ratePerTick,
        Long ticksRemainingEstimate,
        Long ticksRemainingWorstCase,
        Long ticksRemainingBestCase,
        double confidenceScore,
        String confidenceLevel,
        long latestObservedTick,
        int sampleCount) {}
