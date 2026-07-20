package bd.dms.api;

import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The identity of the caller, resolved from their bearer token. Any authenticated role may
 * call this; it is how the SPA re-hydrates the current user after a reload.
 */
@RestController
public class MeController {

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    public record MeResponse(String username, String role, String nameEn, String nameBn) {}

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        return users.findByUsername(authentication.getName())
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private MeResponse toResponse(AppUser user) {
        return new MeResponse(user.getUsername(), user.getRole().name(), user.getNameEn(), user.getNameBn());
    }
}
