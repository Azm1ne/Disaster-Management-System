package bd.dms.dm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One message in a narrow 1:1 thread. Tier 3 of ticket 12 — permitted only along a genuine
 * operational relationship, enforced by {@link DmRelationshipService} before a row is ever
 * written, not by anything in this entity itself.
 */
@Entity
@Table(name = "direct_messages")
public class DirectMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_user_id", nullable = false)
    private Long senderUserId;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DirectMessage() {
        // for JPA
    }

    public DirectMessage(Long senderUserId, Long recipientUserId, String body) {
        this.senderUserId = senderUserId;
        this.recipientUserId = recipientUserId;
        this.body = body;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSenderUserId() {
        return senderUserId;
    }

    public Long getRecipientUserId() {
        return recipientUserId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
