package bd.dms.allocation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One row of an allocation's audit timeline — mirrors {@code AlertTransition} exactly. */
@Entity
@Table(name = "allocation_transitions")
public class AllocationTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "allocation_id", nullable = false)
    private Long allocationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    private AllocationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private AllocationStatus toStatus;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(length = 2000)
    private String note;

    @Column(name = "at_tick", nullable = false)
    private long atTick;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AllocationTransition() {
        // for JPA
    }

    public AllocationTransition(
            Long allocationId,
            AllocationStatus fromStatus,
            AllocationStatus toStatus,
            Long actorUserId,
            String note,
            long atTick) {
        this.allocationId = allocationId;
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

    public Long getAllocationId() {
        return allocationId;
    }

    public AllocationStatus getFromStatus() {
        return fromStatus;
    }

    public AllocationStatus getToStatus() {
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
