package bd.dms.dm;

import bd.dms.dm.dto.DirectMessageView;
import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import java.util.List;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sole writer of DM state. Every send and every thread read is re-checked against
 * {@link DmRelationshipService} — relationships can change (a Camp Manager reassigned to a
 * different camp loses their old Volunteers), so this is re-evaluated fresh rather than cached on
 * the message. Pushed live over STOMP to {@code /topic/dm/<recipientUserId>}, a per-user topic
 * only that user may subscribe to (see {@code StompAuthChannelInterceptor}).
 */
@Service
public class DmService {

    private final DirectMessageRepository messages;
    private final UserRepository users;
    private final DmRelationshipService relationships;
    private final SimpMessagingTemplate messaging;

    public DmService(
            DirectMessageRepository messages,
            UserRepository users,
            DmRelationshipService relationships,
            SimpMessagingTemplate messaging) {
        this.messages = messages;
        this.users = users;
        this.relationships = relationships;
        this.messaging = messaging;
    }

    @Transactional
    public DirectMessage send(AppUser actor, Long recipientUserId, String body) {
        AppUser recipient = users.findById(recipientUserId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown recipient: " + recipientUserId));
        if (!relationships.permitted(actor, recipient)) {
            throw new AccessDeniedException(
                    "No operational relationship permits a DM between these two users");
        }
        DirectMessage saved = messages.save(new DirectMessage(actor.getId(), recipientUserId, body));
        messaging.convertAndSend("/topic/dm/" + recipientUserId, toView(saved));
        return saved;
    }

    public List<DirectMessage> threadWith(AppUser actor, Long otherUserId) {
        AppUser other = users.findById(otherUserId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + otherUserId));
        if (!relationships.permitted(actor, other)) {
            throw new AccessDeniedException(
                    "No operational relationship permits a DM between these two users");
        }
        return messages.findThread(actor.getId(), otherUserId);
    }

    public DirectMessageView toView(DirectMessage message) {
        return new DirectMessageView(
                message.getId(),
                message.getSenderUserId(),
                message.getRecipientUserId(),
                message.getBody(),
                message.getCreatedAt());
    }
}
