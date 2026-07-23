package bd.dms.allocation.dto;

import bd.dms.allocation.AllocationStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record AllocationSummary(
        Long id,
        String resourceType,
        Long sourceCampId,
        Long targetCampId,
        BigDecimal recommendedQuantity,
        BigDecimal decidedQuantity,
        AllocationStatus status,
        double severityScore,
        double shortageScore,
        double populationScore,
        double fairnessScore,
        double priorityScore,
        long generatedAtTick,
        Long decidedAtTick,
        boolean canAct,
        Instant createdAt,
        Instant updatedAt) {}
