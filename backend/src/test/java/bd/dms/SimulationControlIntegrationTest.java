package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import bd.dms.api.SimulationController.SpeedRequest;
import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import bd.dms.sim.SimulationClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The control-surface seam: driving the simulation is a privileged action. Asserts on externally
 * observable HTTP behaviour — that Coordinator/Admin can move the clock, that every other role is
 * refused server-side, and that an unsupported speed is rejected.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimulationControlIntegrationTest {

    private static final String DEMO_PASSWORD = "relief2026";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void coordinatorCanPauseResumeAndReset() {
        String token = tokenFor("coordinator");

        SimulationClock resumed = post("/simulation/resume", token, SimulationClock.class).getBody();
        assertThat(resumed.running()).isTrue();

        SimulationClock paused = post("/simulation/pause", token, SimulationClock.class).getBody();
        assertThat(paused.running()).isFalse();

        SimulationClock reset = post("/simulation/reset", token, SimulationClock.class).getBody();
        assertThat(reset.tick()).isZero();
        assertThat(reset.running()).isFalse();
        assertThat(reset.phase()).isEqualTo("SURGE");
        assertThat(reset.scenarioLength()).isPositive();
    }

    @Test
    void adminCanChangeSpeedAndItIsReflectedInTheClock() {
        String token = tokenFor("admin");

        ResponseEntity<SimulationClock> response = rest.exchange(
                "/simulation/speed", POST, new HttpEntity<>(new SpeedRequest(4.0), bearer(token)),
                SimulationClock.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().speed()).isEqualTo(4.0);

        // Restore a sane default so speed does not leak into other tests sharing this context.
        rest.exchange("/simulation/speed", POST,
                new HttpEntity<>(new SpeedRequest(1.0), bearer(token)), SimulationClock.class);
    }

    @Test
    void anUnsupportedSpeedIsRejected() {
        ResponseEntity<String> response = rest.exchange(
                "/simulation/speed", POST,
                new HttpEntity<>(new SpeedRequest(99.0), bearer(tokenFor("admin"))), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void nonPrivilegedRolesCannotDriveTheSimulation() {
        for (String role : new String[] {"donor", "volunteer", "victim", "ngo", "camp_manager"}) {
            ResponseEntity<String> response =
                    post("/simulation/pause", tokenFor(role), String.class);

            assertThat(response.getStatusCode())
                    .as("%s must not be able to drive the simulation", role)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void drivingTheSimulationRequiresAuthentication() {
        ResponseEntity<String> response =
                rest.exchange("/simulation/pause", POST, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anySignedInRoleCanReadTheClock() {
        ResponseEntity<SimulationClock> response = rest.exchange(
                "/simulation/clock", GET, new HttpEntity<>(bearer(tokenFor("victim"))),
                SimulationClock.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().simTime()).isNotNull();
    }

    private <T> ResponseEntity<T> post(String path, String token, Class<T> type) {
        return rest.exchange(path, POST, new HttpEntity<>(bearer(token)), type);
    }

    private String tokenFor(String username) {
        return rest.postForEntity("/auth/login", new LoginRequest(username, DEMO_PASSWORD),
                AuthResponse.class).getBody().accessToken();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
