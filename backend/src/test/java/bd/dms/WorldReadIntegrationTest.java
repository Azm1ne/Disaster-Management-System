package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;

import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The world-read seam: what an actor observes over HTTP when they read the seeded world.
 * Asserts on the JSON the API actually emits — both disaster worlds, bilingual names,
 * geometry to draw, and (critically) that the public locator leaks nothing sensitive.
 */
class WorldReadIntegrationTest extends PostgresIntegrationTest {

    private static final String DEMO_PASSWORD = "relief2026";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void worldDisastersReturnsBothSeededWorldsWithBilingualNamesAndGeometry() {
        AuthResponse session = login("coordinator", DEMO_PASSWORD);

        ResponseEntity<JsonNode> response =
                rest.exchange("/world/disasters", GET, bearer(session.accessToken()), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode disasters = response.getBody();
        assertThat(disasters).isNotNull();
        assertThat(disasters.isArray()).isTrue();
        assertThat(disasters).hasSize(2);

        List<String> types = new ArrayList<>();
        for (JsonNode disaster : disasters) {
            types.add(disaster.get("type").asText());
            assertThat(disaster.get("nameEn").asText()).isNotBlank();
            assertThat(disaster.get("nameBn").asText()).isNotBlank();

            JsonNode areas = disaster.get("affectedAreas");
            assertThat(areas.isArray()).isTrue();
            assertThat(areas).isNotEmpty();
            // Geometry is a GeoJSON object the client can hand straight to Leaflet, not a string.
            JsonNode geometry = areas.get(0).get("geometry");
            assertThat(geometry.isObject()).isTrue();
            assertThat(geometry.get("type").asText()).isEqualTo("Polygon");

            JsonNode camps = disaster.get("camps");
            assertThat(camps.isArray()).isTrue();
            assertThat(camps).isNotEmpty();
            JsonNode camp = camps.get(0);
            assertThat(camp.get("nameEn").asText()).isNotBlank();
            assertThat(camp.get("nameBn").asText()).isNotBlank();
            assertThat(camp.get("lat").isNumber()).isTrue();
            assertThat(camp.get("lng").isNumber()).isTrue();
        }
        assertThat(types).containsExactlyInAnyOrder("FLOOD", "CYCLONE");
    }

    @Test
    void campDetailReturnsCoreStateWithResourcesAndBilingualNames() {
        AuthResponse session = login("camp_manager", DEMO_PASSWORD);
        long campId = firstCampId(session);

        ResponseEntity<JsonNode> response = rest.exchange(
                "/world/camps/" + campId, GET, bearer(session.accessToken()), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode camp = response.getBody();
        assertThat(camp.get("nameEn").asText()).isNotBlank();
        assertThat(camp.get("nameBn").asText()).isNotBlank();
        assertThat(camp.get("capacity").isNumber()).isTrue();
        assertThat(camp.get("population").isNumber()).isTrue();
        assertThat(camp.get("disaster").get("nameBn").asText()).isNotBlank();
        assertThat(camp.get("resources").isArray()).isTrue();
        assertThat(camp.get("resources")).isNotEmpty();
        assertThat(camp.get("resources").get(0).get("type").asText()).isNotBlank();
    }

    @Test
    void unknownCampIdIsNotFound() {
        AuthResponse session = login("coordinator", DEMO_PASSWORD);

        ResponseEntity<String> response = rest.exchange(
                "/world/camps/999999", GET, bearer(session.accessToken()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void worldReadRequiresAuthentication() {
        ResponseEntity<String> response = rest.getForEntity("/world/disasters", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void publicLocatorNeedsNoLoginAndExposesOnlyNameLocationAndStatus() {
        // No Authorization header at all — a displaced person has no account.
        ResponseEntity<JsonNode> response = rest.getForEntity("/public/camps", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode camps = response.getBody();
        assertThat(camps.isArray()).isTrue();
        assertThat(camps).isNotEmpty();

        for (JsonNode camp : camps) {
            assertThat(camp.get("nameEn").asText()).isNotBlank();
            assertThat(camp.get("nameBn").asText()).isNotBlank();
            assertThat(camp.get("lat").isNumber()).isTrue();
            assertThat(camp.get("status").asText()).isIn("OPEN", "CLOSED");
            // The whitelist: no operational field may leak to unauthenticated callers.
            assertThat(camp.has("capacity")).isFalse();
            assertThat(camp.has("population")).isFalse();
            assertThat(camp.has("resources")).isFalse();
        }
    }

    private long firstCampId(AuthResponse session) {
        JsonNode disasters =
                rest.exchange("/world/disasters", GET, bearer(session.accessToken()), JsonNode.class).getBody();
        return disasters.get(0).get("camps").get(0).get("id").asLong();
    }

    private AuthResponse login(String username, String password) {
        ResponseEntity<AuthResponse> response =
                rest.postForEntity("/auth/login", new LoginRequest(username, password), AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }
}
