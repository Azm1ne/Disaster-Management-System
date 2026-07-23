package bd.dms.alert.dto;

import java.util.List;

public record AlertDetail(AlertSummary summary, List<TransitionView> transitions, List<NoteView> notes) {}
