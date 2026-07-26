package bd.dms.api;

import bd.dms.dm.DirectMessage;
import bd.dms.dm.DmRelationshipService;
import bd.dms.dm.DmService;
import bd.dms.dm.dto.ContactView;
import bd.dms.dm.dto.DirectMessageView;
import bd.dms.user.AppUser;
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
 * Tier 3 of ticket 12: narrow 1:1 DMs, permitted only along a genuine operational relationship.
 * Enforcement lives in {@link bd.dms.dm.DmRelationshipService} / {@link DmService}; this
 * controller only resolves the caller and shapes responses.
 */
@RestController
@RequestMapping("/dms")
public class DmController {

    public record SendRequest(@NotNull Long recipientUserId, @NotBlank String body) {}

    private final DmService dmService;
    private final DmRelationshipService relationships;
    private final UserRepository users;

    public DmController(DmService dmService, DmRelationshipService relationships, UserRepository users) {
        this.dmService = dmService;
        this.relationships = relationships;
        this.users = users;
    }

    @GetMapping("/contacts")
    public List<ContactView> contacts(Authentication authentication) {
        return relationships.contactsFor(actor(authentication)).stream()
                .map(u -> new ContactView(u.getId(), u.getRole(), u.getNameEn(), u.getNameBn()))
                .toList();
    }

    @GetMapping("/thread/{otherUserId}")
    public List<DirectMessageView> thread(@PathVariable Long otherUserId, Authentication authentication) {
        return dmService.threadWith(actor(authentication), otherUserId).stream()
                .map(dmService::toView)
                .toList();
    }

    @PostMapping
    public DirectMessageView send(@Valid @RequestBody SendRequest request, Authentication authentication) {
        DirectMessage saved = dmService.send(actor(authentication), request.recipientUserId(), request.body());
        return dmService.toView(saved);
    }

    private AppUser actor(Authentication authentication) {
        return users.findByUsername(authentication.getName()).orElseThrow();
    }
}
