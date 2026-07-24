package bd.dms.allocation.dto;

import bd.dms.allocation.AllocationStatus;
import java.time.Instant;

public record AllocationTransitionView(
        AllocationStatus fromStatus,
        AllocationStatus toStatus,
        Long actorUserId,
        String note,
        long atTick,
        Instant createdAt) {}
