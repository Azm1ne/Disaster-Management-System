package bd.dms.broadcast;

import bd.dms.broadcast.dto.BroadcastView;
import bd.dms.user.AppUser;
import bd.dms.user.Role;
import java.util.List;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sole writer of broadcast/read-receipt state (mirrors {@code AlertService}). A broadcast is
 * role-scoped, not per-camp: "every Camp Manager" or "every Volunteer", since the ticket asks for
 * that granularity, not per-camp fan-out. Pushed live over STOMP to {@code /topic/broadcasts/<role>};
 * {@code StompAuthChannelInterceptor} is the access-control boundary for who may subscribe there.
 */
@Service
public class BroadcastService {

    private static final List<Role> BROADCASTABLE_ROLES = List.of(Role.CAMP_MANAGER, Role.VOLUNTEER);

    private final BroadcastRepository broadcasts;
    private final BroadcastReadRepository reads;
    private final SimpMessagingTemplate messaging;

    public BroadcastService(
            BroadcastRepository broadcasts, BroadcastReadRepository reads, SimpMessagingTemplate messaging) {
        this.broadcasts = broadcasts;
        this.reads = reads;
        this.messaging = messaging;
    }

    @Transactional
    public Broadcast send(AppUser actor, Role targetRole, String bodyEn, String bodyBn) {
        if (!isOversight(actor)) {
            throw new AccessDeniedException("Only Coordinator/Admin may broadcast");
        }
        if (!BROADCASTABLE_ROLES.contains(targetRole)) {
            throw new IllegalArgumentException("Broadcasts may only target Camp Manager or Volunteer");
        }
        Broadcast saved = broadcasts.save(new Broadcast(actor.getId(), targetRole, bodyEn, bodyBn));
        messaging.convertAndSend("/topic/broadcasts/" + targetRole, toView(saved));
        return saved;
    }

    /** Coordinator/Admin see everything they (or a peer) sent; everyone else sees only
     * broadcasts targeted at their own role. */
    public List<Broadcast> visibleTo(AppUser actor) {
        if (isOversight(actor)) {
            return broadcasts.findAllByOrderByCreatedAtDesc();
        }
        return broadcasts.findByTargetRoleOrderByCreatedAtDesc(actor.getRole());
    }

    @Transactional
    public void markRead(AppUser actor, Long broadcastId) {
        Broadcast broadcast = broadcasts.findById(broadcastId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown broadcast: " + broadcastId));
        if (broadcast.getTargetRole() != actor.getRole()) {
            throw new AccessDeniedException("Not a recipient of this broadcast");
        }
        if (!reads.existsByBroadcastIdAndUserId(broadcastId, actor.getId())) {
            reads.save(new BroadcastRead(broadcastId, actor.getId()));
        }
    }

    /** Who has read a broadcast, visible only to its sender or oversight — the read-receipt
     * feature this ticket asks for ("so the sender knows it landed"). */
    public List<BroadcastRead> receiptsFor(AppUser actor, Long broadcastId) {
        Broadcast broadcast = broadcasts.findById(broadcastId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown broadcast: " + broadcastId));
        if (!isOversight(actor) && !broadcast.getSenderUserId().equals(actor.getId())) {
            throw new AccessDeniedException("Only the sender may view read receipts");
        }
        return reads.findByBroadcastIdOrderByReadAtAsc(broadcastId);
    }

    public BroadcastView toView(Broadcast broadcast) {
        return new BroadcastView(
                broadcast.getId(),
                broadcast.getSenderUserId(),
                broadcast.getTargetRole(),
                broadcast.getBodyEn(),
                broadcast.getBodyBn(),
                broadcast.getCreatedAt());
    }

    private boolean isOversight(AppUser actor) {
        return actor.getRole() == Role.COORDINATOR || actor.getRole() == Role.ADMIN;
    }
}
