package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;

import bd.dms.api.MeController.MeResponse;
import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import bd.dms.auth.dto.TokenRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The API-boundary seam for auth (Seam 1). Every assertion is on externally observable HTTP
 * behavior — status codes and token payloads — given a seeded actor, never on internal wiring.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {

    private static final String DEMO_PASSWORD = "relief2026";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void loginReturnsTokensAndRoleClaim() {
        AuthResponse body = login("coordinator", DEMO_PASSWORD);

        assertThat(body.accessToken()).isNotBlank();
        assertThat(body.refreshToken()).isNotBlank();
        assertThat(body.role()).isEqualTo("COORDINATOR");
        assertThat(body.tokenType()).isEqualTo("Bearer");
        assertThat(body.nameBn()).isNotBlank(); // bilingual identity travels with the token
    }

    @Test
    void wrongPasswordIsRejected() {
        ResponseEntity<String> response = rest.postForEntity(
                "/auth/login", new LoginRequest("coordinator", "not-the-password"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpointRequiresAToken() {
        ResponseEntity<String> response = rest.getForEntity("/me", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authorizedRequestReachesTheProtectedEndpoint() {
        AuthResponse session = login("volunteer", DEMO_PASSWORD);

        ResponseEntity<MeResponse> response = rest.exchange(
                "/me", GET, bearer(session.accessToken()), MeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().username()).isEqualTo("volunteer");
        assertThat(response.getBody().role()).isEqualTo("VOLUNTEER");
    }

    @Test
    void adminRoleReachesTheAdminEndpoint() {
        AuthResponse session = login("admin", DEMO_PASSWORD);

        ResponseEntity<String> response = rest.exchange(
                "/admin/users", GET, bearer(session.accessToken()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("coordinator");
    }

    @Test
    void nonAdminRoleIsRefusedTheAdminEndpointServerSide() {
        AuthResponse session = login("coordinator", DEMO_PASSWORD);

        ResponseEntity<String> response = rest.exchange(
                "/admin/users", GET, bearer(session.accessToken()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void refreshRotatesAndInvalidatesTheOldToken() {
        AuthResponse session = login("donor", DEMO_PASSWORD);

        ResponseEntity<AuthResponse> refreshed = rest.postForEntity(
                "/auth/refresh", new TokenRequest(session.refreshToken()), AuthResponse.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody().accessToken()).isNotBlank();

        // The rotated-away token must no longer be usable.
        ResponseEntity<String> reuse = rest.postForEntity(
                "/auth/refresh", new TokenRequest(session.refreshToken()), String.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        AuthResponse session = login("ngo", DEMO_PASSWORD);

        ResponseEntity<Void> logout = rest.postForEntity(
                "/auth/logout", new TokenRequest(session.refreshToken()), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> afterLogout = rest.postForEntity(
                "/auth/refresh", new TokenRequest(session.refreshToken()), String.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private AuthResponse login(String username, String password) {
        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/auth/login", new LoginRequest(username, password), AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }
}
