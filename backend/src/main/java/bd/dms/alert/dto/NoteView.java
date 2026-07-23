package bd.dms.alert.dto;

import java.time.Instant;

public record NoteView(Long authorUserId, String body, Instant createdAt) {}
