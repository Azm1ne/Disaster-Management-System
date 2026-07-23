package bd.dms.note;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Generic threaded notes. Alert code (and, from ticket 12, allocation/transfer code) calls this
 * rather than touching {@link NoteRepository} directly, so the subject-type/subject-id pairing
 * stays in one place.
 */
@Service
public class NoteService {

    private final NoteRepository notes;

    public NoteService(NoteRepository notes) {
        this.notes = notes;
    }

    public Note add(NoteSubjectType subjectType, Long subjectId, Long authorUserId, String body) {
        return notes.save(new Note(subjectType, subjectId, authorUserId, body));
    }

    public List<Note> forSubject(NoteSubjectType subjectType, Long subjectId) {
        return notes.findBySubjectTypeAndSubjectIdOrderByCreatedAtAsc(subjectType, subjectId);
    }
}
