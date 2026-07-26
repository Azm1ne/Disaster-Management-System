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

/**
 * One open "shift" derived 1:1 from an alert (see {@link VolunteerTaskGenerationService}), moving
 * OPEN -&gt; ASSIGNED (push or self-accept) or OPEN -&gt; CANCELLED (source alert closed first).
 * {@link VolunteerTaskService} is the sole writer of {@code status}/{@code assignedVolunteerId} —
 * mirrors {@code AlertService} and {@code AllocationService} being the sole writers of their
 * respective state.
 */
@Entity
@Table(name = "volunteer_tasks")
public class VolunteerTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false, unique = true)
    private Long alertId;

    @Column(name = "camp_id", nullable = false)
    private Long campId;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_skill", nullable = false)
    private Skill requiredSkill;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VolunteerTaskStatus status;

    @Column(name = "assigned_volunteer_id")
    private Long assignedVolunteerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_method")
    private AssignmentMethod assignmentMethod;

    @Column(name = "urgency_score", nullable = false)
    private double urgencyScore;

    @Column(name = "generated_at_tick", nullable = false)
    private long generatedAtTick;

    @Column(name = "assigned_at_tick")
    private Long assignedAtTick;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VolunteerTask() {
        // for JPA
    }

    public VolunteerTask(
            Long alertId, Long campId, Skill requiredSkill, String description, double urgencyScore,
            long generatedAtTick) {
        this.alertId = alertId;
        this.campId = campId;
        this.requiredSkill = requiredSkill;
        this.description = description;
        this.status = VolunteerTaskStatus.OPEN;
        this.urgencyScore = urgencyScore;
        this.generatedAtTick = generatedAtTick;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getAlertId() {
        return alertId;
    }

    public Long getCampId() {
        return campId;
    }

    public Skill getRequiredSkill() {
        return requiredSkill;
    }

    public String getDescription() {
        return description;
    }

    public VolunteerTaskStatus getStatus() {
        return status;
    }

    public Long getAssignedVolunteerId() {
        return assignedVolunteerId;
    }

    public AssignmentMethod getAssignmentMethod() {
        return assignmentMethod;
    }

    public double getUrgencyScore() {
        return urgencyScore;
    }

    public long getGeneratedAtTick() {
        return generatedAtTick;
    }

    public Long getAssignedAtTick() {
        return assignedAtTick;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Called only by {@code VolunteerTaskGenerationService}, refreshing urgency on a still-OPEN
     * task in place instead of duplicating it (its alert already has a unique task by construction
     * via the DB unique constraint on {@code alert_id}). */
    public void refreshUrgency(double urgencyScore) {
        if (status != VolunteerTaskStatus.OPEN) {
            return;
        }
        this.urgencyScore = urgencyScore;
        this.updatedAt = Instant.now();
    }

    /** Called only by {@code VolunteerTaskService} — the sole writer of assignment state. */
    public void assignTo(Long volunteerId, AssignmentMethod method, long atTick) {
        this.status = VolunteerTaskStatus.ASSIGNED;
        this.assignedVolunteerId = volunteerId;
        this.assignmentMethod = method;
        this.assignedAtTick = atTick;
        this.updatedAt = Instant.now();
    }

    /** Called only by {@code VolunteerTaskGenerationService} when the source alert closes/resolves
     * before anyone took the still-OPEN task. */
    public void cancel() {
        this.status = VolunteerTaskStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }
}
