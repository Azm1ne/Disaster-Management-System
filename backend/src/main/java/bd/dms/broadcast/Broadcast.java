package bd.dms.broadcast;

import bd.dms.user.Role;
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
 * A bilingual announcement from a Coordinator/Admin to every user of one role (Camp Manager or
 * Volunteer) — never an open chat, and never targeted at an individual (that is a DM, tier 3).
 */
@Entity
@Table(name = "broadcasts")
public class Broadcast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_user_id", nullable = false)
    private Long senderUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", nullable = false)
    private Role targetRole;

    @Column(name = "body_en", nullable = false, length = 2000)
    private String bodyEn;

    @Column(name = "body_bn", nullable = false, length = 2000)
    private String bodyBn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Broadcast() {
        // for JPA
    }

    public Broadcast(Long senderUserId, Role targetRole, String bodyEn, String bodyBn) {
        this.senderUserId = senderUserId;
        this.targetRole = targetRole;
        this.bodyEn = bodyEn;
        this.bodyBn = bodyBn;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSenderUserId() {
        return senderUserId;
    }

    public Role getTargetRole() {
        return targetRole;
    }

    public String getBodyEn() {
        return bodyEn;
    }

    public String getBodyBn() {
        return bodyBn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
