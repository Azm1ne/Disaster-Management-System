package bd.dms.anomaly.dto;

import bd.dms.anomaly.AnomalyDetectorType;
import bd.dms.anomaly.AnomalyFlagStatus;
import java.time.Instant;
import java.util.List;

public record AnomalyFlagView(
        Long id,
        AnomalyDetectorType detectorType,
        double score,
        String summary,
        String innocentExplanation,
        List<Long> subjectIds,
        Long detectedAtTick,
        AnomalyFlagStatus status,
        Long reviewedByUserId,
        String reviewNote,
        Instant reviewedAt,
        Instant createdAt) {}
