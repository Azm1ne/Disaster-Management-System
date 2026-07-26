package bd.dms.note.dto;

import java.time.Instant;

/** Shared note view: alert case notes (ticket 06) and allocation/transfer case notes (ticket 12)
 * both render through this one shape. */
public record NoteView(Long authorUserId, String body, Instant createdAt) {}
