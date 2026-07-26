package bd.dms.api;

import bd.dms.broadcast.Broadcast;
import bd.dms.broadcast.BroadcastService;
import bd.dms.broadcast.dto.BroadcastReadView;
import bd.dms.broadcast.dto.BroadcastView;
import bd.dms.user.AppUser;
import bd.dms.user.Role;
import bd.dms.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tier 2 of ticket 12: bilingual Coordinator/Admin -> Camp Manager|Volunteer announcements, with
 * read receipts. Role/broadcastability is enforced in {@link BroadcastService}; this controller
 * only resolves the caller and shapes responses.
 */
@RestController
@RequestMapping("/broadcasts")
public class BroadcastController {

    public record SendRequest(@NotNull Role targetRole, @NotBlank String bodyEn, @NotBlank String bodyBn) {}

    private final BroadcastService broadcastService;
    private final UserRepository users;

    public BroadcastController(BroadcastService broadcastService, UserRepository users) {
        this.broadcastService = broadcastService;
        this.users = users;
    }

    @GetMapping
    public List<BroadcastView> list(Authentication authentication) {
        return broadcastService.visibleTo(actor(authentication)).stream()
                .map(broadcastService::toView)
                .toList();
    }

    @PostMapping
    public BroadcastView send(@Valid @RequestBody SendRequest request, Authentication authentication) {
        Broadcast broadcast = broadcastService.send(
                actor(authentication), request.targetRole(), request.bodyEn(), request.bodyBn());
        return broadcastService.toView(broadcast);
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable Long id, Authentication authentication) {
        broadcastService.markRead(actor(authentication), id);
    }

    @GetMapping("/{id}/receipts")
    public List<BroadcastReadView> receipts(@PathVariable Long id, Authentication authentication) {
        return broadcastService.receiptsFor(actor(authentication), id).stream()
                .map(read -> new BroadcastReadView(read.getUserId(), read.getReadAt()))
                .toList();
    }

    private AppUser actor(Authentication authentication) {
        return users.findByUsername(authentication.getName()).orElseThrow();
    }
}
