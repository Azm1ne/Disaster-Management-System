package bd.dms.dm.dto;

import java.time.Instant;

public record DirectMessageView(Long id, Long senderUserId, Long recipientUserId, String body, Instant createdAt) {}
