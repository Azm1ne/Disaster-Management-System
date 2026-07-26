package bd.dms.volunteer;

import bd.dms.sim.SimulationEngine;
import bd.dms.user.AppUser;
import bd.dms.user.Role;
import bd.dms.volunteer.dto.SkillCoverage;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sole writer of task assignment state (mirrors {@code AlertService}/{@code AllocationService}).
 * Two independent paths reach the same {@link VolunteerTask#assignTo}: a coordinator push-assigns
 * a specific volunteer, or a volunteer self-accepts their own profile from the open-shifts board.
 * Both are guarded by the same "still OPEN" check, so a race between the two can't double-book a
 * task — the loser gets an {@link IllegalStateException} from a task that is no longer OPEN.
 */
@Service
public class VolunteerTaskService {

    private final VolunteerTaskRepository tasks;
    private final VolunteerTaskTransitionRepository transitions;
    private final VolunteerProfileRepository volunteers;
    private final VolunteerScoringService scoring;
    private final CampRepository camps;
    private final SimulationEngine engine;

    public VolunteerTaskService(
            VolunteerTaskRepository tasks,
            VolunteerTaskTransitionRepository transitions,
            VolunteerProfileRepository volunteers,
            VolunteerScoringService scoring,
            CampRepository camps,
            SimulationEngine engine) {
        this.tasks = tasks;
        this.transitions = transitions;
        this.volunteers = volunteers;
        this.scoring = scoring;
        this.camps = camps;
        this.engine = engine;
    }

    /** One scored candidate for a task, best-fit first. */
    public record Candidate(
            VolunteerProfile volunteer, double skillScore, double distanceKm, double distanceScore,
            double urgencyScore, double score) {}

    /** Coordinator/Admin only: every volunteer in the roster, ranked by skill x distance x
     * urgency, best fit first — a volunteer lacking the required skill scores 0 and sorts last. */
    public List<Candidate> candidatesFor(AppUser actor, Long taskId) {
        requireOversight(actor);
        VolunteerTask task = requireTask(taskId);
        return volunteers.findAll().stream()
                .map(v -> score(v, task))
                .sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .toList();
    }

    private Candidate score(VolunteerProfile v, VolunteerTask task) {
        Camp camp = camps.findById(task.getCampId())
                .orElseThrow(() -> new IllegalStateException("Unknown camp: " + task.getCampId()));
        double skillScore = scoring.skillScore(v, task.getRequiredSkill());
        double distanceKm = scoring.distanceKm(v.getLat(), v.getLng(), camp.getLat(), camp.getLng());
        double distanceScore = scoring.distanceScore(distanceKm);
        double urgencyScore = task.getUrgencyScore();
        return new Candidate(v, skillScore, distanceKm, distanceScore, urgencyScore,
                scoring.score(skillScore, distanceScore, urgencyScore));
    }

    @Transactional
    public VolunteerTask pushAssign(AppUser actor, Long taskId, Long volunteerId, String note) {
        requireOversight(actor);
        VolunteerTask task = requireTask(taskId);
        requireOpen(task);
        VolunteerProfile volunteer = volunteers.findById(volunteerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown volunteer: " + volunteerId));
        return assign(task, volunteer.getId(), AssignmentMethod.PUSH, actor.getId(), note);
    }

    @Transactional
    public VolunteerTask selfAccept(AppUser actor, Long taskId) {
        if (actor.getRole() != Role.VOLUNTEER) {
            throw new AccessDeniedException("Only a volunteer can self-accept a shift");
        }
        VolunteerProfile volunteer = volunteers.findByUserId(actor.getId())
                .orElseThrow(() -> new IllegalStateException("No volunteer profile for this account"));
        VolunteerTask task = requireTask(taskId);
        requireOpen(task);
        return assign(task, volunteer.getId(), AssignmentMethod.SELF, actor.getId(), null);
    }

    private VolunteerTask assign(
            VolunteerTask task, Long volunteerId, AssignmentMethod method, Long actorUserId, String note) {
        long tick = engine.currentTick();
        VolunteerTaskStatus from = task.getStatus();
        task.assignTo(volunteerId, method, tick);
        transitions.save(new VolunteerTaskTransition(
                task.getId(), from, VolunteerTaskStatus.ASSIGNED, actorUserId, note, tick));
        return task;
    }

    /** Coordinator/Admin see every task; a volunteer sees the open-shifts board (every OPEN task)
     * plus their own assignments — {@code myAssignments} is the dedicated call for the latter. */
    public List<VolunteerTask> visibleTo(AppUser actor) {
        if (isOversight(actor)) {
            return tasks.findAll();
        }
        if (actor.getRole() != Role.VOLUNTEER) {
            return List.of();
        }
        return tasks.findByStatus(VolunteerTaskStatus.OPEN);
    }

    /** A volunteer's own accepted shifts and push-assignments, in one place. */
    public List<VolunteerTask> myAssignments(AppUser actor) {
        return volunteers.findByUserId(actor.getId())
                .map(v -> tasks.findByAssignedVolunteerId(v.getId()))
                .orElse(List.of());
    }

    /** Coordinator/Admin only: per skill, how many OPEN tasks need it versus how many roster
     * volunteers with that skill are not currently tied up on an ASSIGNED task — the gap panel's
     * data. A volunteer with no matching skill anywhere in the roster shows every one of that
     * skill's OPEN tasks as a hard gap. */
    public List<SkillCoverage> skillGap(AppUser actor) {
        requireOversight(actor);
        Map<Skill, Long> openBySkill = new EnumMap<>(Skill.class);
        for (VolunteerTask task : tasks.findByStatus(VolunteerTaskStatus.OPEN)) {
            openBySkill.merge(task.getRequiredSkill(), 1L, Long::sum);
        }
        Set<Long> busyVolunteerIds = tasks.findByStatus(VolunteerTaskStatus.ASSIGNED).stream()
                .map(VolunteerTask::getAssignedVolunteerId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Skill, Long> availableBySkill = new EnumMap<>(Skill.class);
        for (VolunteerProfile v : volunteers.findAll()) {
            if (busyVolunteerIds.contains(v.getId())) {
                continue;
            }
            for (Skill skill : v.getSkills()) {
                availableBySkill.merge(skill, 1L, Long::sum);
            }
        }
        List<SkillCoverage> coverage = new java.util.ArrayList<>();
        for (Skill skill : Skill.values()) {
            long open = openBySkill.getOrDefault(skill, 0L);
            long available = availableBySkill.getOrDefault(skill, 0L);
            long gap = Math.max(0, open - available);
            coverage.add(new SkillCoverage(skill, open, available, gap, gap > 0));
        }
        return coverage;
    }

    public boolean canAssign(AppUser actor, VolunteerTask task) {
        return isOversight(actor) && task.getStatus() == VolunteerTaskStatus.OPEN;
    }

    public boolean canAccept(AppUser actor, VolunteerTask task) {
        return actor.getRole() == Role.VOLUNTEER && task.getStatus() == VolunteerTaskStatus.OPEN;
    }

    private VolunteerTask requireTask(Long taskId) {
        return tasks.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown task: " + taskId));
    }

    private void requireOpen(VolunteerTask task) {
        if (task.getStatus() != VolunteerTaskStatus.OPEN) {
            throw new IllegalStateException("Task is no longer open: " + task.getId());
        }
    }

    private void requireOversight(AppUser actor) {
        if (!isOversight(actor)) {
            throw new AccessDeniedException("Only Coordinator/Admin may do this");
        }
    }

    private boolean isOversight(AppUser actor) {
        return actor.getRole() == Role.COORDINATOR || actor.getRole() == Role.ADMIN;
    }
}
