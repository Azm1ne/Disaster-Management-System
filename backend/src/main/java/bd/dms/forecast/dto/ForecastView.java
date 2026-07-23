package bd.dms.forecast.dto;

import java.math.BigDecimal;

public record ForecastView(
        Long campId,
        String campCode,
        String campNameEn,
        String campNameBn,
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
