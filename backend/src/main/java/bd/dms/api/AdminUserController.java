package bd.dms.api;

import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * An administrator's view of the seeded accounts. This is the representative role-gated
 * endpoint for the slice: {@code /admin/**} requires the ADMIN role, so any other role's
 * token is refused here server-side (403), not merely hidden in the UI.
 */
@RestController
@RequestMapping("/admin")
public class AdminUserController {

    private final UserRepository users;

    public AdminUserController(UserRepository users) {
        this.users = users;
    }

    public record AdminUserView(Long id, String username, String role, String nameEn, String nameBn) {}

    @GetMapping("/users")
    public List<AdminUserView> users() {
        return users.findAll().stream().map(this::toView).toList();
    }

    private AdminUserView toView(AppUser user) {
        return new AdminUserView(user.getId(), user.getUsername(), user.getRole().name(),
                user.getNameEn(), user.getNameBn());
    }
}
