package bd.dms.family;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A household registered as a single unit — a solo victim is simply a group of one. One group
 * per registering user (enforced by the unique {@code owner_user_id}), so "my group" is
 * unambiguous. Arrival needs both the representative and the destination camp's manager to
 * confirm it, so each is tracked as its own flag rather than a single status the two sides
 * would have to race to set.
 */
@Entity
@Table(name = "family_groups")
public class FamilyGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false, unique = true)
    private Long ownerUserId;

    @Column(name = "camp_id", nullable = false)
    private Long campId;

    @Column(name = "group_name", nullable = false)
    private String groupName;

    @Column(name = "representative_arrived", nullable = false)
    private boolean representativeArrived;

    @Column(name = "manager_confirmed_arrived", nullable = false)
    private boolean managerConfirmedArrived;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    protected FamilyGroup() {
        // for JPA
    }

    public FamilyGroup(Long ownerUserId, Long campId, String groupName) {
        this.ownerUserId = ownerUserId;
        this.campId = campId;
        this.groupName = groupName;
        this.registeredAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public Long getCampId() {
        return campId;
    }

    public String getGroupName() {
        return groupName;
    }

    public boolean isRepresentativeArrived() {
        return representativeArrived;
    }

    public boolean isManagerConfirmedArrived() {
        return managerConfirmedArrived;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void markRepresentativeArrived() {
        this.representativeArrived = true;
    }

    public void markManagerConfirmedArrived() {
        this.managerConfirmedArrived = true;
    }
}
