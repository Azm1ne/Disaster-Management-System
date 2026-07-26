package bd.dms.broadcast;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * One recipient's read receipt for one broadcast. Its existence is the read signal — there is no
 * separate "delivered but unread" row, mirroring {@link bd.dms.world.CampAssignment}'s
 * composite-key style.
 */
@Entity
@Table(name = "broadcast_reads")
@IdClass(BroadcastRead.Key.class)
public class BroadcastRead {

    @Id
    @Column(name = "broadcast_id")
    private Long broadcastId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    protected BroadcastRead() {
        // for JPA
    }

    public BroadcastRead(Long broadcastId, Long userId) {
        this.broadcastId = broadcastId;
        this.userId = userId;
        this.readAt = Instant.now();
    }

    public Long getBroadcastId() {
        return broadcastId;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public record Key(Long broadcastId, Long userId) implements Serializable {}

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BroadcastRead other)) {
            return false;
        }
        return Objects.equals(broadcastId, other.broadcastId) && Objects.equals(userId, other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(broadcastId, userId);
    }
}
