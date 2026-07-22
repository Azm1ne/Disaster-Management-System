package bd.dms.api;

import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import bd.dms.world.CampAssignment;
import bd.dms.world.CampAssignmentRepository;
import java.util.List;
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
    private final CampAssignmentRepository assignments;

    public MeController(UserRepository users, CampAssignmentRepository assignments) {
        this.users = users;
        this.assignments = assignments;
    }

    /**
     * {@code campIds} are the camps this user manages — what the SPA needs to know which camp
     * topic to subscribe to. It is empty for every role that manages no camp.
     */
    public record MeResponse(
            String username, String role, String nameEn, String nameBn, List<Long> campIds) {}

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        return users.findByUsername(authentication.getName())
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private MeResponse toResponse(AppUser user) {
        List<Long> campIds = assignments.findByUserId(user.getId()).stream()
                .map(CampAssignment::getCampId)
                .toList();
        return new MeResponse(
                user.getUsername(), user.getRole().name(), user.getNameEn(), user.getNameBn(), campIds);
    }
}
