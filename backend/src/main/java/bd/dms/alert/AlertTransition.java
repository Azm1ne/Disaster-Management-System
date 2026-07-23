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

/** One row of an alert's audit timeline: a single state change, who made it, and when. */
@Entity
@Table(name = "alert_transitions")
public class AlertTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    private AlertStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private AlertStatus toStatus;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(length = 2000)
    private String note;

    @Column(name = "at_tick", nullable = false)
    private long atTick;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AlertTransition() {
        // for JPA
    }

    /** {@code actorUserId} null means the transition was system/SLA-triggered. */
    public AlertTransition(
            Long alertId,
            AlertStatus fromStatus,
            AlertStatus toStatus,
            Long actorUserId,
            String note,
            long atTick) {
        this.alertId = alertId;
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

    public Long getAlertId() {
        return alertId;
    }

    public AlertStatus getFromStatus() {
        return fromStatus;
    }

    public AlertStatus getToStatus() {
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
