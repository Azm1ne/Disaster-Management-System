package bd.dms.config;

import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import bd.dms.world.Camp;
import bd.dms.world.CampAssignment;
import bd.dms.world.CampAssignmentRepository;
import bd.dms.world.CampRepository;
import java.util.Optional;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Binds the demo Camp Manager (and, from ticket 12, the demo Volunteer) to the camp they operate
 * at, so per-camp realtime authorization has something real to check, and so the Camp Manager
 * &lt;-&gt; Volunteer DM relationship ({@code DmRelationshipService}) has a real pair to permit
 * out of the box. Runs after {@link DemoUserSeeder} (users must exist first) and is idempotent,
 * so it is safe on every boot and in tests.
 */
@Component
@Order(2)
public class CampAssignmentSeeder implements CommandLineRunner {

    private static final String CAMP_MANAGER_USERNAME = "camp_manager";
    private static final String VOLUNTEER_USERNAME = "volunteer";
    private static final String MANAGED_CAMP_CODE = "jam-kurigram-sadar";

    private final UserRepository users;
    private final CampRepository camps;
    private final CampAssignmentRepository assignments;

    public CampAssignmentSeeder(
            UserRepository users, CampRepository camps, CampAssignmentRepository assignments) {
        this.users = users;
        this.camps = camps;
        this.assignments = assignments;
    }

    @Override
    public void run(String... args) {
        Optional<Camp> camp = camps.findByCode(MANAGED_CAMP_CODE);
        if (camp.isEmpty()) {
            return;
        }
        Long campId = camp.get().getId();
        assign(CAMP_MANAGER_USERNAME, campId);
        assign(VOLUNTEER_USERNAME, campId);
    }

    private void assign(String username, Long campId) {
        Optional<AppUser> user = users.findByUsername(username);
        if (user.isEmpty()) {
            return;
        }
        Long userId = user.get().getId();
        if (!assignments.existsByUserIdAndCampId(userId, campId)) {
            assignments.save(new CampAssignment(userId, campId));
        }
    }
}
