package bd.dms.alert;

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
 * An alert raised against a camp, moving through a server-enforced lifecycle (see
 * {@link AlertTransitionRules}). {@link AlertService} is the sole writer of {@code status} and
 * {@code updatedAt} — every change also appends an {@link AlertTransition} row, which is the
 * audit timeline.
 */
@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType type;

    @Column(name = "camp_id", nullable = false)
    private Long campId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "raised_by_user_id", nullable = false)
    private Long raisedByUserId;

    @Column(name = "raised_at_tick", nullable = false)
    private long raisedAtTick;

    @Column(name = "sla_deadline_tick", nullable = false)
    private long slaDeadlineTick;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Alert() {
        // for JPA
    }

    public Alert(
            AlertType type,
            Long campId,
            String description,
            Long raisedByUserId,
            long raisedAtTick,
            long slaDeadlineTick) {
        this.type = type;
        this.campId = campId;
        this.status = AlertStatus.NEW;
        this.description = description;
        this.raisedByUserId = raisedByUserId;
        this.raisedAtTick = raisedAtTick;
        this.slaDeadlineTick = slaDeadlineTick;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public AlertType getType() {
        return type;
    }

    public Long getCampId() {
        return campId;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public Long getRaisedByUserId() {
        return raisedByUserId;
    }

    public long getRaisedAtTick() {
        return raisedAtTick;
    }

    public long getSlaDeadlineTick() {
        return slaDeadlineTick;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Mutator exists solely for AlertService, the sole writer of alert state.
    void setStatus(AlertStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
