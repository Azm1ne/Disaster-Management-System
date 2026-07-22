package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;

import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import bd.dms.world.CampRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Victim/family registration, dual-source arrival, and reunification search, walked through as
 * one scenario against the one seeded demo Victim account (a unique-constraint on the owner,
 * so registering twice is itself a behaviour under test) and the demo Camp Manager's own camp.
 * Ordered because each step's assertions depend on state the previous step created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FamilyIntegrationTest {

    private static final String DEMO_PASSWORD = "relief2026";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CampRepository camps;

    private static long groupId;
    private static long medicalMemberId;

    @Test
    @Order(1)
    void victimRegistersAHouseholdWithDerivedMemberCount() {
        long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();
        String body = """
                {"campId": %d, "groupName": "Rahman Household",
                 "members": [{"nickname": "Abbu", "ageBand": "ADULT"},
                             {"nickname": "Nodi", "ageBand": "CHILD"}]}
                """.formatted(campId);

        ResponseEntity<JsonNode> response = post("/family/register", "victim", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode group = response.getBody();
        assertThat(group.get("groupName").asText()).isEqualTo("Rahman Household");
        assertThat(group.get("memberCount").asInt()).isEqualTo(2);
        assertThat(group.get("status").asText()).isEqualTo("REGISTERED");
        assertThat(group.get("representativeArrived").asBoolean()).isFalse();
        assertThat(group.get("managerConfirmedArrived").asBoolean()).isFalse();
        assertThat(group.get("members")).hasSize(2);

        groupId = group.get("id").asLong();
        medicalMemberId = group.get("members").get(1).get("id").asLong();
    }

    @Test
    @Order(2)
    void aSecondRegistrationForTheSameAccountIsRejected() {
        long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();
        String body = """
                {"campId": %d, "groupName": "Second Attempt", "members": [{"nickname": "X", "ageBand": "ADULT"}]}
                """.formatted(campId);

        ResponseEntity<JsonNode> response = post("/family/register", "victim", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(3)
    void victimCanReadTheirOwnGroupStatus() {
        ResponseEntity<JsonNode> response = rest.exchange(
                "/family/me", GET, new HttpEntity<>(bearer("victim")), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("id").asLong()).isEqualTo(groupId);
    }

    @Test
    @Order(4)
    void aCampManagerNotAssignedToTheCampIsForbidden() {
        long otherCampId = camps.findByCode("jam-chilmari").orElseThrow().getId();

        ResponseEntity<String> response = rest.exchange(
                "/camp/" + otherCampId + "/arrivals", GET, new HttpEntity<>(bearer("camp_manager")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(5)
    void dualSourceArrivalMovesFromRegisteredThroughArrivingToArrived() {
        long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();

        JsonNode afterRepTap = post("/family/me/arrived", "victim", null).getBody();
        assertThat(afterRepTap.get("status").asText()).isEqualTo("ARRIVING");
        assertThat(afterRepTap.get("representativeArrived").asBoolean()).isTrue();
        assertThat(afterRepTap.get("managerConfirmedArrived").asBoolean()).isFalse();

        ResponseEntity<JsonNode> beforeConfirm = rest.exchange(
                "/camp/" + campId + "/arrivals", GET, new HttpEntity<>(bearer("camp_manager")), JsonNode.class);
        assertThat(beforeConfirm.getBody().get("arrivingCount").asInt()).isEqualTo(1);
        assertThat(beforeConfirm.getBody().get("arrivedCount").asInt()).isEqualTo(0);

        JsonNode afterManagerConfirm =
                post("/camp/" + campId + "/arrivals/" + groupId + "/confirm", "camp_manager", null).getBody();
        assertThat(afterManagerConfirm.get("status").asText()).isEqualTo("ARRIVED");

        ResponseEntity<JsonNode> afterConfirm = rest.exchange(
                "/camp/" + campId + "/arrivals", GET, new HttpEntity<>(bearer("camp_manager")), JsonNode.class);
        assertThat(afterConfirm.getBody().get("arrivingCount").asInt()).isEqualTo(0);
        assertThat(afterConfirm.getBody().get("arrivedCount").asInt()).isEqualTo(1);
    }

    @Test
    @Order(6)
    void staffAloneCanSetTheMedicalFlagAndItNeverReachesReunificationSearch() {
        long campId = camps.findByCode("jam-kurigram-sadar").orElseThrow().getId();
        HttpHeaders headers = bearer("camp_manager");
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Void> response = rest.exchange(
                "/camp/" + campId + "/arrivals/" + groupId + "/members/" + medicalMemberId + "/medical-flag",
                PATCH, new HttpEntity<>("{\"medicalFlag\": true}", headers), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode mine = rest.exchange("/family/me", GET, new HttpEntity<>(bearer("victim")), JsonNode.class).getBody();
        assertThat(mine.get("members").get(1).get("medicalFlag").asBoolean()).isTrue();

        JsonNode results = rest.getForEntity("/public/family-search?q=Rahman", JsonNode.class).getBody();
        assertThat(results).hasSize(1);
        JsonNode result = results.get(0);
        assertThat(result.get("groupName").asText()).isEqualTo("Rahman Household");
        assertThat(result.get("status").asText()).isEqualTo("ARRIVED");
        assertThat(result.has("members")).isFalse();
        assertThat(result.has("memberCount")).isFalse();
        assertThat(result.has("medicalFlag")).isFalse();
    }

    @Test
    @Order(7)
    void reunificationSearchIsSearchNotBrowse() {
        JsonNode blank = rest.getForEntity("/public/family-search?q=", JsonNode.class).getBody();
        assertThat(blank).isEmpty();

        ResponseEntity<JsonNode> noQuery = rest.getForEntity("/public/family-search", JsonNode.class);
        assertThat(noQuery.getBody()).isEmpty();
    }

    private ResponseEntity<JsonNode> post(String path, String username, String body) {
        HttpHeaders headers = bearer(username);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private HttpHeaders bearer(String username) {
        String token = rest.postForEntity("/auth/login", new LoginRequest(username, DEMO_PASSWORD), AuthResponse.class)
                .getBody().accessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
