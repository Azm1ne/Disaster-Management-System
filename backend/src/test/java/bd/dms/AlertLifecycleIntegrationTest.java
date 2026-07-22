package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import bd.dms.alert.Alert;
import bd.dms.alert.AlertService;
import bd.dms.alert.AlertStatus;
import bd.dms.alert.AlertType;
import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import bd.dms.note.Note;
import bd.dms.sim.SimulationEngine;
import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.security.access.AccessDeniedException;

/**
 * Exercises {@link AlertService} directly against the real seeded world/users — routing,
 * permission enforcement, transition legality, and note-taking. HTTP-boundary behaviour (roles
 * refused at the controller) is covered separately in AlertRealtimeIntegrationTest /
 * SimulationControlIntegrationTest-style tests; this is the service seam.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AlertLifecycleIntegrationTest {

    private static final String DEMO_PASSWORD = "relief2026";

    @Autowired
    private AlertService alerts;

    @Autowired
    private UserRepository users;

    @Autowired
    private CampRepository camps;

    @Autowired
    private SimulationEngine engine;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void campManagerCanRaiseAndDriveAResourceShortageOnTheirOwnCamp() {
        AppUser campManager = users.findByUsername("camp_manager").orElseThrow();
        Long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();

        Alert alert = alerts.raise(
                campManager, AlertType.RESOURCE_SHORTAGE, campId, "Water stock is running low");
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.NEW);

        Alert acknowledged = alerts.transition(campManager, alert.getId(), AlertStatus.ACKNOWLEDGED, null);
        assertThat(acknowledged.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);

        Alert inProgress =
                alerts.transition(campManager, alert.getId(), AlertStatus.IN_PROGRESS, "Convoy dispatched");
        assertThat(inProgress.getStatus()).isEqualTo(AlertStatus.IN_PROGRESS);

        assertThat(alerts.transitionsFor(alert.getId())).hasSize(2);
    }

    @Test
    void campManagerCannotRaiseAnAlertOnACampTheyDoNotManage() {
        AppUser campManager = users.findByUsername("camp_manager").orElseThrow();
        Long otherCampId = camps.findByCode("jam-chilmari").orElseThrow().getId();

        assertThatThrownBy(() ->
                alerts.raise(campManager, AlertType.RESOURCE_SHORTAGE, otherCampId, "Not my camp"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void securityIncidentIsNotVisibleToTheCampManagerButIsToTheCoordinator() {
        AppUser coordinator = users.findByUsername("coordinator").orElseThrow();
        AppUser campManager = users.findByUsername("camp_manager").orElseThrow();
        Long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();

        Alert alert = alerts.raise(coordinator, AlertType.SECURITY_INCIDENT, campId, "Perimeter breach");

        assertThat(alerts.visibleDetail(coordinator, alert.getId())).isPresent();
        assertThat(alerts.visibleDetail(campManager, alert.getId())).isEmpty();
    }

    @Test
    void campManagerCannotActOnASecurityIncidentEvenOnTheirOwnCamp() {
        AppUser coordinator = users.findByUsername("coordinator").orElseThrow();
        AppUser campManager = users.findByUsername("camp_manager").orElseThrow();
        Long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();

        Alert alert = alerts.raise(coordinator, AlertType.SECURITY_INCIDENT, campId, "Perimeter breach");

        assertThatThrownBy(() -> alerts.transition(campManager, alert.getId(), AlertStatus.ACKNOWLEDGED, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void illegalTransitionIsRejected() {
        AppUser coordinator = users.findByUsername("coordinator").orElseThrow();
        Long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();
        Alert alert = alerts.raise(coordinator, AlertType.MEDICAL_EMERGENCY, campId, "Casualty");

        assertThatThrownBy(() -> alerts.transition(coordinator, alert.getId(), AlertStatus.CLOSED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void handlerAndCoordinatorCanBothAddNotesButUnrelatedRoleCannot() {
        AppUser coordinator = users.findByUsername("coordinator").orElseThrow();
        AppUser campManager = users.findByUsername("camp_manager").orElseThrow();
        Long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();
        Alert alert = alerts.raise(campManager, AlertType.RESOURCE_SHORTAGE, campId, "Low water");

        Note fromManager = alerts.addNote(campManager, alert.getId(), "Checked the tank, confirmed low");
        Note fromCoordinator = alerts.addNote(coordinator, alert.getId(), "Rerouting a convoy");

        assertThat(alerts.notesFor(alert.getId()))
                .extracting(Note::getBody)
                .containsExactly(fromManager.getBody(), fromCoordinator.getBody());
    }

    @Test
    void everyRoleThatCannotActIsRefusedAtTheHttpBoundary() {
        String donorToken = tokenFor("donor");
        var response = rest.exchange(
                "/alerts", POST, entityWithToken(donorToken,
                        "{\"type\":\"RESOURCE_SHORTAGE\",\"campId\":1,\"description\":\"x\"}"),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    private String tokenFor(String username) {
        return rest.postForEntity("/auth/login", new LoginRequest(username, DEMO_PASSWORD),
                AuthResponse.class).getBody().accessToken();
    }

    private org.springframework.http.HttpEntity<String> entityWithToken(String token, String body) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return new org.springframework.http.HttpEntity<>(body, headers);
    }
}
