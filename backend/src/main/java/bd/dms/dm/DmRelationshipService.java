package bd.dms.dm;

import bd.dms.user.AppUser;
import bd.dms.user.Role;
import bd.dms.user.UserRepository;
import bd.dms.world.CampAssignment;
import bd.dms.world.CampAssignmentRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The single source of truth for which pairs of users may exchange a DM: Coordinator &lt;-&gt;
 * Camp Manager (Coordinators have whole-operation oversight, so any Camp Manager qualifies), and
 * Camp Manager &lt;-&gt; a Volunteer who shares at least one of their camp assignments. Every
 * other pair — including anything involving Admin, Donor, Victim, or NGO — is refused; this is
 * the enforcement point the ticket's acceptance criteria asks integration tests to cover.
 */
@Service
public class DmRelationshipService {

    private final CampAssignmentRepository assignments;
    private final UserRepository users;

    public DmRelationshipService(CampAssignmentRepository assignments, UserRepository users) {
        this.assignments = assignments;
        this.users = users;
    }

    public boolean permitted(AppUser a, AppUser b) {
        if (a.getId().equals(b.getId())) {
            return false;
        }
        if (isPair(a, b, Role.COORDINATOR, Role.CAMP_MANAGER)) {
            return true;
        }
        if (isPair(a, b, Role.CAMP_MANAGER, Role.VOLUNTEER)) {
            return shareACamp(a, b);
        }
        return false;
    }

    /** Every user the given actor is currently permitted to DM, for the frontend to offer as
     * contacts rather than a free-text user picker. */
    public List<AppUser> contactsFor(AppUser actor) {
        return switch (actor.getRole()) {
            case COORDINATOR -> users.findByRole(Role.CAMP_MANAGER);
            case CAMP_MANAGER -> concat(users.findByRole(Role.COORDINATOR), campSharingVolunteers(actor));
            case VOLUNTEER -> campSharingManagers(actor);
            default -> List.of();
        };
    }

    private List<AppUser> campSharingVolunteers(AppUser campManager) {
        Set<Long> camps = campIdsFor(campManager);
        return users.findByRole(Role.VOLUNTEER).stream()
                .filter(volunteer -> campIdsFor(volunteer).stream().anyMatch(camps::contains))
                .toList();
    }

    private List<AppUser> campSharingManagers(AppUser volunteer) {
        Set<Long> camps = campIdsFor(volunteer);
        return users.findByRole(Role.CAMP_MANAGER).stream()
                .filter(manager -> campIdsFor(manager).stream().anyMatch(camps::contains))
                .toList();
    }

    private boolean shareACamp(AppUser a, AppUser b) {
        Set<Long> campsOfA = campIdsFor(a);
        return campIdsFor(b).stream().anyMatch(campsOfA::contains);
    }

    private Set<Long> campIdsFor(AppUser user) {
        return assignments.findByUserId(user.getId()).stream()
                .map(CampAssignment::getCampId)
                .collect(Collectors.toSet());
    }

    private boolean isPair(AppUser a, AppUser b, Role roleOne, Role roleTwo) {
        return (a.getRole() == roleOne && b.getRole() == roleTwo)
                || (a.getRole() == roleTwo && b.getRole() == roleOne);
    }

    private List<AppUser> concat(List<AppUser> first, List<AppUser> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }
}
