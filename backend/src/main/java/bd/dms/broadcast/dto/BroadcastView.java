package bd.dms.broadcast.dto;

import bd.dms.user.Role;
import java.time.Instant;

public record BroadcastView(
        Long id, Long senderUserId, Role targetRole, String bodyEn, String bodyBn, Instant createdAt) {}
