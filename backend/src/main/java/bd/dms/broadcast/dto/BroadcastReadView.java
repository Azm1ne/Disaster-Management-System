package bd.dms.broadcast.dto;

import java.time.Instant;

public record BroadcastReadView(Long userId, Instant readAt) {}
