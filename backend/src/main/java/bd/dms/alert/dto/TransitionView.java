package bd.dms.alert.dto;

import bd.dms.alert.AlertStatus;
import java.time.Instant;

public record TransitionView(
        AlertStatus fromStatus, AlertStatus toStatus, Long actorUserId, String note, long atTick, Instant createdAt) {}
