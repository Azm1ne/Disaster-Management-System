package bd.dms.volunteer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Audit timeline of a task's status changes — mirrors {@code AllocationTransition} exactly.
 * {@code actorUserId} is null for the system-driven cancel-on-alert-close transition. */
@Entity
@Table(name = "volunteer_task_transitions")
public class VolunteerTaskTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    private VolunteerTaskStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private VolunteerTaskStatus toStatus;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(length = 2000)
    private String note;

    @Column(name = "at_tick", nullable = false)
    private long atTick;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VolunteerTaskTransition() {
        // for JPA
    }

    public VolunteerTaskTransition(
            Long taskId, VolunteerTaskStatus fromStatus, VolunteerTaskStatus toStatus,
            Long actorUserId, String note, long atTick) {
        this.taskId = taskId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorUserId = actorUserId;
        this.note = note;
        this.atTick = atTick;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public VolunteerTaskStatus getFromStatus() {
        return fromStatus;
    }

    public VolunteerTaskStatus getToStatus() {
        return toStatus;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getNote() {
        return note;
    }

    public long getAtTick() {
        return atTick;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
