package bd.dms.api;

import bd.dms.user.AppUser;
import bd.dms.user.Role;
import bd.dms.user.UserRepository;
import bd.dms.volunteer.VolunteerProfile;
import bd.dms.volunteer.VolunteerProfileRepository;
import bd.dms.volunteer.VolunteerRouteService;
import bd.dms.volunteer.VolunteerTask;
import bd.dms.volunteer.VolunteerTaskService;
import bd.dms.volunteer.dto.RouteView;
import bd.dms.volunteer.dto.SkillCoverage;
import bd.dms.volunteer.dto.VolunteerCandidateView;
import bd.dms.volunteer.dto.VolunteerTaskSummary;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The volunteer matching surface: the task queue (coordinator's full view / volunteer's
 * open-shifts board), scored candidates for push-assign, self-accept, "my assignments", the route
 * to an assigned task, and the skill-coverage gap panel. Role/task entitlement is enforced in
 * {@link VolunteerTaskService}; this controller only resolves the caller and shapes responses.
 */
@RestController
@RequestMapping("/volunteers")
public class VolunteerController {

    public record AssignRequest(@NotNull Long volunteerId, String note) {}

    private final VolunteerTaskService taskService;
    private final VolunteerRouteService routeService;
    private final VolunteerProfileRepository volunteers;
    private final CampRepository camps;
    private final UserRepository users;

    public VolunteerController(
            VolunteerTaskService taskService,
            VolunteerRouteService routeService,
            VolunteerProfileRepository volunteers,
            CampRepository camps,
            UserRepository users) {
        this.taskService = taskService;
        this.routeService = routeService;
        this.volunteers = volunteers;
        this.camps = camps;
        this.users = users;
    }

    @GetMapping("/tasks")
    public List<VolunteerTaskSummary> tasks(Authentication authentication) {
        AppUser actor = actor(authentication);
        return taskService.visibleTo(actor).stream().map(t -> toSummary(t, actor)).toList();
    }

    @GetMapping("/tasks/mine")
    public List<VolunteerTaskSummary> myTasks(Authentication authentication) {
        AppUser actor = actor(authentication);
        return taskService.myAssignments(actor).stream().map(t -> toSummary(t, actor)).toList();
    }

    @GetMapping("/tasks/{id}/candidates")
    public List<VolunteerCandidateView> candidates(@PathVariable Long id, Authentication authentication) {
        AppUser actor = actor(authentication);
        return taskService.candidatesFor(actor, id).stream()
                .map(c -> new VolunteerCandidateView(
                        c.volunteer().getId(),
                        c.volunteer().getNameEn(),
                        c.volunteer().getNameBn(),
                        c.skillScore() > 0,
                        c.distanceKm(),
                        c.skillScore(),
                        c.distanceScore(),
                        c.urgencyScore(),
                        c.score()))
                .toList();
    }

    @PostMapping("/tasks/{id}/assign")
    public VolunteerTaskSummary assign(
            @PathVariable Long id, @Valid @RequestBody AssignRequest request, Authentication authentication) {
        AppUser actor = actor(authentication);
        VolunteerTask task = taskService.pushAssign(actor, id, request.volunteerId(), request.note());
        return toSummary(task, actor);
    }

    @PostMapping("/tasks/{id}/accept")
    public VolunteerTaskSummary accept(@PathVariable Long id, Authentication authentication) {
        AppUser actor = actor(authentication);
        VolunteerTask task = taskService.selfAccept(actor, id);
        return toSummary(task, actor);
    }

    @GetMapping("/tasks/{id}/route")
    public RouteView route(@PathVariable Long id, Authentication authentication) {
        AppUser actor = actor(authentication);
        VolunteerTask task = taskService.visibleTo(actor).stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .or(() -> taskService.myAssignments(actor).stream().filter(t -> t.getId().equals(id)).findFirst())
                .orElseThrow(() -> new IllegalArgumentException("Unknown task: " + id));
        if (task.getAssignedVolunteerId() == null) {
            throw new IllegalStateException("Task has no assigned volunteer yet: " + id);
        }
        VolunteerProfile volunteer = volunteers.findById(task.getAssignedVolunteerId()).orElseThrow();
        boolean isTheAssignedVolunteer = volunteer.getUserId() != null && volunteer.getUserId().equals(actor.getId());
        if (!isOversight(actor) && !isTheAssignedVolunteer) {
            throw new AccessDeniedException("Not entitled to this task's route");
        }
        Camp camp = camps.findById(task.getCampId()).orElseThrow();
        return routeService.routeTo(volunteer, camp);
    }

    @GetMapping("/skill-gap")
    public List<SkillCoverage> skillGap(Authentication authentication) {
        return taskService.skillGap(actor(authentication));
    }

    private AppUser actor(Authentication authentication) {
        return users.findByUsername(authentication.getName()).orElseThrow();
    }

    private boolean isOversight(AppUser actor) {
        return actor.getRole() == Role.COORDINATOR || actor.getRole() == Role.ADMIN;
    }

    private VolunteerTaskSummary toSummary(VolunteerTask task, AppUser actor) {
        VolunteerProfile assignee = task.getAssignedVolunteerId() == null
                ? null
                : volunteers.findById(task.getAssignedVolunteerId()).orElse(null);
        return new VolunteerTaskSummary(
                task.getId(),
                task.getAlertId(),
                task.getCampId(),
                task.getRequiredSkill(),
                task.getDescription(),
                task.getStatus(),
                task.getAssignedVolunteerId(),
                assignee == null ? null : assignee.getNameEn(),
                assignee == null ? null : assignee.getNameBn(),
                task.getAssignmentMethod(),
                task.getUrgencyScore(),
                task.getGeneratedAtTick(),
                task.getAssignedAtTick(),
                taskService.canAssign(actor, task),
                taskService.canAccept(actor, task),
                task.getCreatedAt(),
                task.getUpdatedAt());
    }
}
