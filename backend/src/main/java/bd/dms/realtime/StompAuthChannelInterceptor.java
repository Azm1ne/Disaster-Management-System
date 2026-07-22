package bd.dms.realtime;

import bd.dms.auth.JwtService;
import bd.dms.user.UserRepository;
import bd.dms.world.CampAssignmentRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.List;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Makes topic subscription the access-control boundary. Every inbound STOMP frame passes through
 * here: {@code CONNECT} must carry a valid {@code Authorization: Bearer <jwt>} or the connection
 * is refused, and every {@code SUBSCRIBE} is checked against what the principal's role — and, for
 * a Camp Manager, their camp assignment — actually entitles them to.
 *
 * <p>Entitlement:
 * <ul>
 *   <li>{@code /topic/simulation}, {@code /topic/world} — any authenticated user.</li>
 *   <li>{@code /topic/camp/{id}} — Coordinator and Admin see any camp; a Camp Manager sees only
 *       a camp they are assigned to. Everyone else is refused.</li>
 * </ul>
 * A refused subscription throws, so the client receives an ERROR frame and no messages — the
 * transport itself enforces access rather than the UI hiding it.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";
    private static final String CAMP_PREFIX = "/topic/camp/";

    private final JwtService jwtService;
    private final UserRepository users;
    private final CampAssignmentRepository assignments;

    public StompAuthChannelInterceptor(
            JwtService jwtService, UserRepository users, CampAssignmentRepository assignments) {
        this.jwtService = jwtService;
        this.users = users;
        this.assignments = assignments;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setUser(authenticate(accessor));
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor.getUser(), accessor.getDestination());
        }
        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            throw new AccessDeniedException("A bearer token is required to open a realtime session");
        }
        try {
            Claims claims = jwtService.parse(header.substring(BEARER.length())).getPayload();
            var authority = new SimpleGrantedAuthority("ROLE_" + claims.get("role", String.class));
            return new UsernamePasswordAuthenticationToken(claims.getSubject(), null, List.of(authority));
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AccessDeniedException("Invalid realtime credentials", ex);
        }
    }

    private void authorizeSubscription(java.security.Principal principal, String destination) {
        if (!(principal instanceof Authentication authentication) || destination == null) {
            throw new AccessDeniedException("Not authenticated for realtime topics");
        }
        if (destination.equals("/topic/simulation") || destination.equals("/topic/world")) {
            return; // the shared world picture — any authenticated user
        }
        if (destination.startsWith(CAMP_PREFIX)) {
            authorizeCampTopic(authentication, destination.substring(CAMP_PREFIX.length()));
            return;
        }
        throw new AccessDeniedException("Unknown topic: " + destination);
    }

    private void authorizeCampTopic(Authentication authentication, String campIdSegment) {
        if (hasRole(authentication, "ROLE_COORDINATOR") || hasRole(authentication, "ROLE_ADMIN")) {
            return; // whole-operation oversight
        }
        if (!hasRole(authentication, "ROLE_CAMP_MANAGER")) {
            throw new AccessDeniedException("This role is not entitled to camp topics");
        }
        long campId;
        try {
            campId = Long.parseLong(campIdSegment);
        } catch (NumberFormatException ex) {
            throw new AccessDeniedException("Malformed camp topic", ex);
        }
        Long userId = users.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Unknown principal"))
                .getId();
        if (!assignments.existsByUserIdAndCampId(userId, campId)) {
            throw new AccessDeniedException("Not assigned to this camp");
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> role.equals(granted.getAuthority()));
    }
}
