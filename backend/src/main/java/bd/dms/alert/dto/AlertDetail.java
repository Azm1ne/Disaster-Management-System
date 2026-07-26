package bd.dms.alert.dto;

import bd.dms.note.dto.NoteView;
import java.util.List;

public record AlertDetail(AlertSummary summary, List<TransitionView> transitions, List<NoteView> notes) {}
