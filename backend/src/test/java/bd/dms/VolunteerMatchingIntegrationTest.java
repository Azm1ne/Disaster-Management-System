package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.dms.alert.Alert;
import bd.dms.alert.AlertService;
import bd.dms.alert.AlertType;
import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import bd.dms.volunteer.VolunteerProfileRepository;
import bd.dms.volunteer.VolunteerTask;
import bd.dms.volunteer.VolunteerTaskRepository;
import bd.dms.volunteer.VolunteerTaskService;
import bd.dms.volunteer.VolunteerTaskStatus;
import bd.dms.volunteer.VolunteerTaskTransitionRepository;
import bd.dms.volunteer.dto.SkillCoverage;
import bd.dms.world.CampRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

/**
 * Exercises the volunteer matching service seam directly against the real seeded world/users
 * (mirrors AlertLifecycleIntegrationTest): task generation off an alert, scoring order, the
 * push-assign vs self-accept paths, and the skill-coverage gap panel. Cleans up its own tables in
 * before/after each test since (like AllocationControllerIntegrationTest) the Spring test context
 * — and its H2 database — is shared across every test class in the run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VolunteerMatchingIntegrationTest {

    @Autowired
    private AlertService alerts;

    @Autowired
    private VolunteerTaskService taskService;

    @Autowired
    private VolunteerTaskRepository tasks;

    @Autowired
    private VolunteerTaskTransitionRepository transitions;

    @Autowired
    private VolunteerProfileRepository volunteers;

    @Autowired
    private UserRepository users;

    @Autowired
    private CampRepository camps;

    @BeforeEach
    @AfterEach
    void cleanUpTasks() {
        transitions.deleteAll();
        tasks.deleteAll();
    }

    @Test
    void raisingAMedicalEmergencyGeneratesAnOpenMedicalTask() {
        AppUser coordinator = users.findByUsername("coordinator").orElseThrow();
        Long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();

        Alert alert = alerts.raise(coordinator, AlertType.MEDICAL_EMERGENCY, campId, "Casualties at the gate");

        VolunteerTask task = tasks.findByAlertId(alert.getId()).orElseThrow();
        assertThat(task.getRequiredSkill().name()).isEqualTo("MEDICAL");
        assertThat(task.getStatus()).isEqualTo(VolunteerTaskStatus.OPEN);
        assertThat(task.getCampId()).isEqualTo(campId);
    }

    @Test
    void candidatesAreRankedBySkillThenDistanceBestFitFirst() {
        AppUser coordinator = users.findByUsername("coordinator").orElseThrow();
        Long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();
        Alert alert = alerts.raise(coordinator, AlertType.MEDICAL_EMERGENCY, campId, "Casualties");
        VolunteerTask task = tasks.findByAlertId(alert.getId()).orElseThrow();

        List<VolunteerTaskService.Candidate> ranked = taskService.candidatesFor(coordinator, task.getId());

        assertThat(ranked).isNotEmpty();
        // vol-sabbir is seeded ~1km from jam-kurigram-sadar with MEDICAL skill — the clear best fit.
        assertThat(ranked.get(0).volunteer().getNameEn()).isEqualTo("Sabbir Rahman");
        // Scores are sorted strictly descending.
        for (int i = 1; i < ranked.size(); i++) {
            assertThat(ranked.get(i - 1).score()).isGreaterThanOrEqualTo(ranked.get(i).score());
        }
        // A volunteer with no MEDICAL skill anywhere scores exactly zero and sorts at the very end.
        assertThat(ranked.get(ranked.size() - 1).score()).isEqualTo(0.0);
    }

    @Test
    void coordinatorCanPushAssignAnyCandidateRegardlessOfRank() {
        AppUser coordinator = users.findByUsername("coordinator").orElseThrow();
        Long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();
        Alert alert = alerts.raise(coordinator, AlertType.MEDICAL_EMERGENCY, campId, "Casualties");
        VolunteerTask task = tasks.findByAlertId(alert.getId()).orElseThrow();
        var nasrin = volunteers.findByCode("vol-nasrin").orElseThrow();

        VolunteerTask assigned = taskService.pushAssign(coordinator, task.getId(), nasrin.getId(), "Sending Nasrin");

        assertThat(assigned.getStatus()).isEqualTo(VolunteerTaskStatus.ASSIGNED);
        assertThat(assigned.getAssignedVolunteerId()).isEqualTo(nasrin.getId());
        assertThat(assigned.getAssignmentMethod().name()).isEqualTo("PUSH");
    }

    @Test
    void campManagerCannotPushAssign() {
        AppUser coordinator = users.findByUsername("coordinator").orElseThrow();
        AppUser campManager = users.findByUsername("camp_manager").orElseThrow();
        Long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();
        Alert alert = alerts.raise(coordinator, AlertType.MEDICAL_EMERGENCY, campId, "Casualties");
        VolunteerTask task = tasks.findByAlertId(alert.getId()).orElseThrow();
        var nasrin = volunteers.findByCode("vol-nasrin").orElseThrow();

        assertThatThrownBy(() -> taskService.pushAssign(campManager, task.getId(), nasrin.getId(), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void volunteerCanSelfAcceptAnOpenTaskAndSeeItInMyAssignments() {
        AppUser coordinator = users.findByUsername("coordinator").orElseThrow();
        AppUser volunteerUser = users.findByUsername("volunteer").orElseThrow();
        Long campId = camps.findByCode("jam-ulipur").orElseThrow().getId();
        Alert alert = alerts.raise(coordinator, AlertType.RESOURCE_SHORTAGE, campId, "Distribution needed");
        VolunteerTask task = tasks.findByAlertId(alert.getId()).orElseThrow();

        VolunteerTask accepted = taskService.selfAccept(volunteerUser, task.getId());

        assertThat(accepted.getStatus()).isEqualTo(VolunteerTaskStatus.ASSIGNED);
        assertThat(accepted.getAssignmentMethod().name()).isEqualTo("SELF");
        assertThat(taskService.myAssignments(volunteerUser))
                .extracting(VolunteerTask::getId)
                .contains(task.getId());
    }

    @Test
    void selfAcceptingAnAlreadyAssignedTaskFails() {
        AppUser coordinator = users.findByUsername("coordinator").orElseThrow();
        AppUser volunteerUser = users.findByUsername("volunteer").orElseThrow();
        Long campId = camps.findByCode("jam-ulipur").orElseThrow().getId();
        Alert alert = alerts.raise(coordinator, AlertType.RESOURCE_SHORTAGE, campId, "Distribution needed");
        VolunteerTask task = tasks.findByAlertId(alert.getId()).orElseThrow();
        var kamal = volunteers.findByCode("vol-kamal").orElseThrow();
        taskService.pushAssign(coordinator, task.getId(), kamal.getId(), "Sending Kamal first");

        assertThatThrownBy(() -> taskService.selfAccept(volunteerUser, task.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void skillCoverageGapShowsSecurityUnmetSinceNoRosterVolunteerHoldsIt() {
        AppUser coordinator = users.findByUsername("coordinator").orElseThrow();
        Long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();
        alerts.raise(coordinator, AlertType.SECURITY_INCIDENT, campId, "Crowd control needed");

        List<SkillCoverage> gap = taskService.skillGap(coordinator);

        SkillCoverage security = gap.stream()
                .filter(c -> c.skill().name().equals("SECURITY"))
                .findFirst()
                .orElseThrow();
        assertThat(security.openTaskCount()).isGreaterThanOrEqualTo(1);
        assertThat(security.availableVolunteerCount()).isEqualTo(0);
        assertThat(security.unmet()).isTrue();
    }
}
