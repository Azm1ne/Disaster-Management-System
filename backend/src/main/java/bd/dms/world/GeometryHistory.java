package bd.dms.world;

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
 * One append-only row recording a single geometry write against a disaster or affected area
 * (ticket 13's {@code DisasterAdminService} is the sole writer). Mirrors the shape of
 * {@code AlertTransition}: immutable, actor-attributed, no soft-delete or versioning machinery.
 */
@Entity
@Table(name = "geometry_history")
public class GeometryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private GeometrySubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "previous_geometry")
    private String previousGeometry;

    @Column(name = "new_geometry", nullable = false)
    private String newGeometry;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GeometryHistory() {
        // for JPA
    }

    /** {@code previousGeometry} null means this is the subject's first geometry write. */
    public GeometryHistory(
            GeometrySubjectType subjectType,
            Long subjectId,
            String previousGeometry,
            String newGeometry,
            Long actorUserId) {
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.previousGeometry = previousGeometry;
        this.newGeometry = newGeometry;
        this.actorUserId = actorUserId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public GeometrySubjectType getSubjectType() {
        return subjectType;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public String getPreviousGeometry() {
        return previousGeometry;
    }

    public String getNewGeometry() {
        return newGeometry;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
