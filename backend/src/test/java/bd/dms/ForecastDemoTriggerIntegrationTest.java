package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;

import bd.dms.alert.Alert;
import bd.dms.alert.AlertRepository;
import bd.dms.alert.AlertType;
import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import bd.dms.forecast.CampResourceObservationRepository;
import bd.dms.world.CampRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ForecastDemoTriggerIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AlertRepository alerts;

    @Autowired
    private CampRepository camps;

    @Autowired
    private CampResourceObservationRepository observations;

    private String loginAs(String username) {
        AuthResponse login = rest.postForObject("/auth/login",
                new LoginRequest(username, "relief2026"), AuthResponse.class);
        return login.accessToken();
    }

    @Test
    void coordinatorTriggeringTheDemoRaisesARealResourceShortageAlert() {
        Long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAs("coordinator"));

        ResponseEntity<Void> response = rest.exchange(
                "/forecasts/demo/" + campId + "/WATER", POST, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        List<Alert> shortageAlerts = alerts.findAll().stream()
                .filter(a -> a.getType() == AlertType.RESOURCE_SHORTAGE
                        && a.getCampId().equals(campId)
                        && "WATER".equals(a.getResourceType()))
                .toList();
        assertThat(shortageAlerts).hasSize(1);
    }

    @Test
    void theSyntheticObservationsDoNotOutliveTheRequest() {
        Long campId = camps.findByCode("jam-nageshwari").orElseThrow().getId();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAs("coordinator"));

        ResponseEntity<Void> response = rest.exchange(
                "/forecasts/demo/" + campId + "/FOOD", POST, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        // The demo endpoint seeds a 5-point depletion window to compute and raise the alert, but
        // must leave camp_resource_observations exactly as it found it — SimulationEngine is the
        // sole writer of real history.
        assertThat(observations
                        .findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(campId, "FOOD", 0))
                .isEmpty();
    }

    @Test
    void anUnknownResourceTypeIsRejected() {
        Long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAs("coordinator"));

        ResponseEntity<Void> response = rest.exchange(
                "/forecasts/demo/" + campId + "/BANANA", POST, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void nonOversightRolesAreRefused() {
        Long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAs("camp_manager"));

        ResponseEntity<Void> response = rest.exchange(
                "/forecasts/demo/" + campId + "/WATER", POST, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
