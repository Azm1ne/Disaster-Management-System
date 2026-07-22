package bd.dms.alert.dto;

import bd.dms.alert.AlertStatus;
import bd.dms.alert.AlertType;
import java.time.Instant;

public record AlertSummary(
        Long id,
        AlertType type,
        AlertStatus status,
        Long campId,
        String description,
        Long raisedByUserId,
        long raisedAtTick,
        long slaDeadlineTick,
        boolean canAct,
        Instant createdAt,
        Instant updatedAt) {}
