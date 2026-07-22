package bd.dms.world;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/**
 * A user's assignment to a camp they manage. Its sole job in this slice is entitlement: the
 * realtime layer checks it to decide whether a Camp Manager may subscribe to a given camp's
 * topic. A composite (user, camp) key — a user may manage more than one camp.
 */
@Entity
@Table(name = "camp_assignments")
@IdClass(CampAssignment.Key.class)
public class CampAssignment {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "camp_id")
    private Long campId;

    protected CampAssignment() {
        // for JPA
    }

    public CampAssignment(Long userId, Long campId) {
        this.userId = userId;
        this.campId = campId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCampId() {
        return campId;
    }

    /** Composite primary key for {@link CampAssignment}. */
    public record Key(Long userId, Long campId) implements Serializable {}

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CampAssignment other)) {
            return false;
        }
        return Objects.equals(userId, other.userId) && Objects.equals(campId, other.campId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, campId);
    }
}
