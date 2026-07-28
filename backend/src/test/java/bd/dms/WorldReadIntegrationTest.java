package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        JsonNode allDisasters = response.getBody();
        assertThat(allDisasters).isNotNull();
        assertThat(allDisasters.isArray()).isTrue();

        // Filtered to the two ticket-3 seeded worlds by code: this class also exercises the
        // ticket-13 write-then-read seam, which registers extra disasters into the same shared
        // Postgres container, so asserting on the seeded pair specifically (not the raw total)
        // keeps this test independent of the other tests' fixtures.
        List<JsonNode> disasters = new ArrayList<>();
        for (JsonNode disaster : allDisasters) {
            String code = disaster.get("code").asText();
            if ("jamuna-flood-2024".equals(code) || "patuakhali-cyclone-2024".equals(code)) {
                disasters.add(disaster);
            }
        }
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

    private static final String POLYGON = """
            {"type":"Polygon","coordinates":[[[90.0,24.0],[90.1,24.0],[90.1,24.1],[90.0,24.1],[90.0,24.0]]]}""";

    /**
     * Ticket 13's real gap: {@code DisasterView} carries {@code geometry} (the admin-drawn
     * boundary) alongside {@code affectedAreas}, so a directly-registered disaster's boundary is
     * actually visible through the same read surface every screen in the app uses — not just
     * queryable off {@code DisasterRepository} the way Tasks 2-4's tests checked it.
     */
    @Test
    void adminCreatedDisasterBoundaryRoundTripsThroughWorldRead() {
        HttpHeaders admin = authHeaders("admin");
        Map<String, String> body = Map.of(
                "code", "read-vis-flood-1", "type", "FLOOD", "nameEn", "Read Vis Flood",
                "nameBn", "রিড ভিস বন্যা", "geometry", POLYGON);
        ResponseEntity<JsonNode> created =
                rest.exchange("/admin/disasters", POST, new HttpEntity<>(body, admin), JsonNode.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        long disasterId = created.getBody().get("id").asLong();

        // Any authenticated role, not just the admin who wrote it, reads the same boundary back.
        AuthResponse campManager = login("camp_manager", DEMO_PASSWORD);
        JsonNode disasters = rest.exchange(
                        "/world/disasters", GET, bearer(campManager.accessToken()), JsonNode.class)
                .getBody();

        JsonNode disaster = findDisasterByCode(disasters, "read-vis-flood-1");
        assertThat(disaster).isNotNull();
        assertThat(disaster.get("id").asLong()).isEqualTo(disasterId);
        assertThat(disaster.get("nameEn").asText()).isEqualTo("Read Vis Flood");
        assertThat(disaster.get("nameBn").asText()).isEqualTo("রিড ভিস বন্যা");
        assertThat(disaster.get("type").asText()).isEqualTo("FLOOD");
        assertThat(disaster.get("status").asText()).isEqualTo("ACTIVE");
        JsonNode geometry = disaster.get("geometry");
        assertThat(geometry.isObject()).isTrue();
        assertThat(geometry.get("type").asText()).isEqualTo("Polygon");
    }

    /**
     * The two seeded demo disasters (ticket 3) predate manual registration and were never given
     * a boundary — proving {@code geometry} degrades to JSON null rather than blowing up the
     * whole read, the other half of the nullable-boundary contract the round-trip test above
     * doesn't cover.
     */
    @Test
    void seededDisasterWithNoAdminDrawnBoundaryHasNullGeometry() {
        AuthResponse coordinator = login("coordinator", DEMO_PASSWORD);
        JsonNode disasters = rest.exchange(
                        "/world/disasters", GET, bearer(coordinator.accessToken()), JsonNode.class)
                .getBody();

        for (JsonNode disaster : disasters) {
            if (disaster.get("code").asText().startsWith("read-vis-")) {
                continue; // disasters created by other tests in this class carry real geometry
            }
            assertThat(disaster.get("geometry").isNull()).isTrue();
        }
    }

    /**
     * Proof that the direct-admin write path (Task 3) reaches the exact same read surface as
     * every other actor — a camp created via {@code POST /admin/disasters/{id}/camps} shows up,
     * with its 3 bootstrapped resources at the correct per-person quantities, through
     * {@code GET /world/camps/{id}} read by an unrelated role.
     */
    @Test
    void adminCreatedCampAndItsBootstrappedResourcesAreVisibleThroughWorldRead() {
        HttpHeaders admin = authHeaders("admin");
        Map<String, String> disasterBody = Map.of(
                "code", "read-vis-flood-2", "type", "FLOOD", "nameEn", "Read Vis Flood 2",
                "nameBn", "রিড ভিস বন্যা ২", "geometry", POLYGON);
        long disasterId = rest.exchange(
                        "/admin/disasters", POST, new HttpEntity<>(disasterBody, admin), JsonNode.class)
                .getBody().get("id").asLong();

        Map<String, Object> campBody = Map.of(
                "code", "read-vis-camp-1", "nameEn", "Read Vis Camp", "nameBn", "রিড ভিস ক্যাম্প",
                "lat", 24.05, "lng", 90.05, "capacity", 500, "initialPopulation", 100);
        ResponseEntity<JsonNode> createdCamp = rest.exchange(
                "/admin/disasters/" + disasterId + "/camps", POST, new HttpEntity<>(campBody, admin), JsonNode.class);
        assertThat(createdCamp.getStatusCode().is2xxSuccessful()).isTrue();
        long campId = createdCamp.getBody().get("id").asLong();

        AuthResponse coordinator = login("coordinator", DEMO_PASSWORD);
        ResponseEntity<JsonNode> response = rest.exchange(
                "/world/camps/" + campId, GET, bearer(coordinator.accessToken()), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode camp = response.getBody();
        assertThat(camp.get("nameEn").asText()).isEqualTo("Read Vis Camp");
        assertThat(camp.get("population").asInt()).isEqualTo(100);
        assertThat(camp.get("disaster").get("id").asLong()).isEqualTo(disasterId);

        Map<String, Double> quantitiesByType = new java.util.HashMap<>();
        for (JsonNode resource : camp.get("resources")) {
            quantitiesByType.put(resource.get("type").asText(), resource.get("quantity").asDouble());
        }
        assertThat(quantitiesByType).containsOnlyKeys("WATER", "FOOD", "MEDICAL");
        // Per-person multipliers from DisasterAdminService, applied to initialPopulation=100.
        assertThat(quantitiesByType.get("WATER")).isEqualTo(300.0);
        assertThat(quantitiesByType.get("FOOD")).isEqualTo(200.0);
        assertThat(quantitiesByType.get("MEDICAL")).isEqualTo(100.0);
    }

    /**
     * The strongest possible proof that "one write path, never two" holds end-to-end: a
     * coordinator's approved {@code CAMP_CREATE} proposal reaches the exact same
     * {@code GET /world/disasters} read surface the direct-admin path does, nested inside its
     * target disaster's {@code camps} list — not just a row Task 4's tests found via
     * {@code CampRepository}.
     */
    @Test
    void approvedCampCreateProposalIsVisibleInsideItsDisasterThroughWorldRead() {
        HttpHeaders coordinator = authHeaders("coordinator");
        HttpHeaders centralAuthority = authHeaders("central_authority");

        AuthResponse anyReader = login("camp_manager", DEMO_PASSWORD);
        JsonNode disastersBefore = rest.exchange(
                        "/world/disasters", GET, bearer(anyReader.accessToken()), JsonNode.class)
                .getBody();
        long targetDisasterId = disastersBefore.get(0).get("id").asLong();

        Map<String, Object> proposeBody = Map.of(
                "proposalType", "CAMP_CREATE",
                "targetDisasterId", targetDisasterId,
                "payload", Map.of(
                        "code", "read-vis-prop-camp", "nameEn", "Proposed Read Vis Camp",
                        "nameBn", "প্রস্তাবিত রিড ভিস ক্যাম্প",
                        "lat", 24.2, "lng", 90.2, "capacity", 300, "initialPopulation", 50));
        long proposalId = rest.exchange("/proposals", POST, new HttpEntity<>(proposeBody, coordinator), JsonNode.class)
                .getBody().get("id").asLong();

        ResponseEntity<JsonNode> approved = rest.exchange(
                "/proposals/" + proposalId + "/approve", POST, new HttpEntity<>(null, centralAuthority), JsonNode.class);
        assertThat(approved.getStatusCode().is2xxSuccessful()).isTrue();

        JsonNode disastersAfter = rest.exchange(
                        "/world/disasters", GET, bearer(anyReader.accessToken()), JsonNode.class)
                .getBody();
        JsonNode targetDisaster = findDisasterById(disastersAfter, targetDisasterId);
        assertThat(targetDisaster).isNotNull();

        boolean campVisible = false;
        for (JsonNode camp : targetDisaster.get("camps")) {
            if ("read-vis-prop-camp".equals(camp.get("code").asText())) {
                campVisible = true;
                assertThat(camp.get("nameEn").asText()).isEqualTo("Proposed Read Vis Camp");
                assertThat(camp.get("population").asInt()).isEqualTo(50);
            }
        }
        assertThat(campVisible).isTrue();
    }

    /**
     * The mirror case: a rejected {@code DISASTER_CREATE} proposal must never appear through
     * {@code GET /world/disasters} — proving rejection's "world untouched" guarantee (already
     * checked at the repository level by Task 4) holds at the real read surface too.
     */
    @Test
    void rejectedDisasterCreateProposalNeverAppearsThroughWorldRead() {
        HttpHeaders coordinator = authHeaders("coordinator");
        HttpHeaders centralAuthority = authHeaders("central_authority");

        Map<String, Object> proposeBody = Map.of(
                "proposalType", "DISASTER_CREATE",
                "payload", Map.of(
                        "code", "read-vis-rejected-flood", "type", "FLOOD", "nameEn", "Rejected Flood",
                        "nameBn", "প্রত্যাখ্যাত বন্যা", "geometry", POLYGON));
        long proposalId = rest.exchange("/proposals", POST, new HttpEntity<>(proposeBody, coordinator), JsonNode.class)
                .getBody().get("id").asLong();

        ResponseEntity<JsonNode> rejected = rest.exchange(
                "/proposals/" + proposalId + "/reject", POST, new HttpEntity<>(null, centralAuthority), JsonNode.class);
        assertThat(rejected.getStatusCode().is2xxSuccessful()).isTrue();

        AuthResponse reader = login("coordinator", DEMO_PASSWORD);
        JsonNode disasters = rest.exchange(
                        "/world/disasters", GET, bearer(reader.accessToken()), JsonNode.class)
                .getBody();
        assertThat(findDisasterByCode(disasters, "read-vis-rejected-flood")).isNull();
    }

    private JsonNode findDisasterByCode(JsonNode disasters, String code) {
        for (JsonNode disaster : disasters) {
            if (code.equals(disaster.get("code").asText())) {
                return disaster;
            }
        }
        return null;
    }

    private JsonNode findDisasterById(JsonNode disasters, long id) {
        for (JsonNode disaster : disasters) {
            if (disaster.get("id").asLong() == id) {
                return disaster;
            }
        }
        return null;
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

    private HttpHeaders authHeaders(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login(username, DEMO_PASSWORD).accessToken());
        return headers;
    }
}
