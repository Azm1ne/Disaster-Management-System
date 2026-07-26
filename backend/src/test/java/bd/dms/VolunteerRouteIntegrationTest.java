package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;

import bd.dms.alert.Alert;
import bd.dms.alert.AlertService;
import bd.dms.alert.AlertType;
import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import bd.dms.volunteer.OsrmClient;
import bd.dms.volunteer.VolunteerProfileRepository;
import bd.dms.volunteer.VolunteerTask;
import bd.dms.volunteer.VolunteerTaskRepository;
import bd.dms.volunteer.VolunteerTaskService;
import bd.dms.volunteer.VolunteerTaskTransitionRepository;
import bd.dms.volunteer.dto.RouteView;
import bd.dms.world.CampRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * The routing surface at the HTTP boundary: once a task is ASSIGNED, its volunteer can fetch a
 * route — a real OSRM polyline when OSRM answers, a straight two-point line when it doesn't. The
 * seeded {@link OsrmClient} bean is swapped for {@link ControllableOsrmClient} (an in-process test
 * double) so both branches are deterministic and require no network access.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(VolunteerRouteIntegrationTest.TestOsrmConfig.class)
class VolunteerRouteIntegrationTest {

    private static final String DEMO_PASSWORD = "relief2026";

    @TestConfiguration
    static class TestOsrmConfig {
        @Bean
        @Primary
        OsrmClient controllableOsrmClient() {
            return new ControllableOsrmClient();
        }
    }

    static class ControllableOsrmClient implements OsrmClient {
        volatile Optional<RoutePolyline> next = Optional.empty();

        @Override
        public Optional<RoutePolyline> route(double originLat, double originLng, double destLat, double destLng) {
            return next;
        }
    }

    @Autowired
    private OsrmClient osrmClient;

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

    @Autowired
    private TestRestTemplate rest;

    private ControllableOsrmClient testOsrm;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        transitions.deleteAll();
        tasks.deleteAll();
        testOsrm = (ControllableOsrmClient) osrmClient;
        testOsrm.next = Optional.empty();
    }

    private VolunteerTask assignedMedicalTask() {
        AppUser coordinator = users.findByUsername("coordinator").orElseThrow();
        Long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();
        Alert alert = alerts.raise(coordinator, AlertType.MEDICAL_EMERGENCY, campId, "Casualties");
        VolunteerTask task = tasks.findByAlertId(alert.getId()).orElseThrow();
        AppUser volunteerUser = users.findByUsername("volunteer").orElseThrow();
        return taskService.selfAccept(volunteerUser, task.getId());
    }

    private HttpHeaders authHeaders(String username) {
        String token = rest.postForObject(
                "/auth/login", new LoginRequest(username, DEMO_PASSWORD), AuthResponse.class).accessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void osrmSuccessReturnsARealPolyline() {
        VolunteerTask task = assignedMedicalTask();
        testOsrm.next = Optional.of(new OsrmClient.RoutePolyline(
                List.of(new double[] {25.80, 89.63}, new double[] {25.81, 89.64}), 1500.0, 300.0));

        ResponseEntity<RouteView> response = rest.exchange(
                "/volunteers/tasks/" + task.getId() + "/route", GET,
                new HttpEntity<>(authHeaders("volunteer")), RouteView.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().source()).isEqualTo("OSRM");
        assertThat(response.getBody().points()).hasSize(2);
        assertThat(response.getBody().distanceMeters()).isEqualTo(1500.0);
    }

    @Test
    void osrmUnavailableFallsBackToAStraightLine() {
        VolunteerTask task = assignedMedicalTask();
        testOsrm.next = Optional.empty();

        ResponseEntity<RouteView> response = rest.exchange(
                "/volunteers/tasks/" + task.getId() + "/route", GET,
                new HttpEntity<>(authHeaders("volunteer")), RouteView.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().source()).isEqualTo("STRAIGHT_LINE");
        assertThat(response.getBody().points()).hasSize(2);
        assertThat(response.getBody().distanceMeters()).isGreaterThan(0);
    }
}
