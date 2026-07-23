package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;

import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import bd.dms.forecast.dto.ForecastView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ForecastControllerIntegrationTest {

    private static final String DEMO_PASSWORD = "relief2026";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void anyAuthenticatedRoleCanReadForecasts() {
        AuthResponse login = rest.postForObject("/auth/login",
                new LoginRequest("donor", DEMO_PASSWORD), AuthResponse.class);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.accessToken());
        ResponseEntity<ForecastView[]> response = rest.exchange(
                "/forecasts", GET, new HttpEntity<>(headers), ForecastView[].class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        ResponseEntity<String> response = rest.getForEntity("/forecasts", String.class);
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }
}
