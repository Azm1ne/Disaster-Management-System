package bd.dms.note;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A threaded note attached to some subject (an alert now; ticket 12 attaches allocations and
 * transfers the same way). Deliberately generic so later tickets do not need a migration to
 * reuse it — see {@link NoteSubjectType}.
 */
@Entity
@Table(name = "notes")
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private NoteSubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Note() {
        // for JPA
    }

    public Note(NoteSubjectType subjectType, Long subjectId, Long authorUserId, String body) {
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.authorUserId = authorUserId;
        this.body = body;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public NoteSubjectType getSubjectType() {
        return subjectType;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public Long getAuthorUserId() {
        return authorUserId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
