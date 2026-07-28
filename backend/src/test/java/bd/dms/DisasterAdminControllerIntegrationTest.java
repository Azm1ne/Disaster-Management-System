package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import bd.dms.world.dto.AffectedAreaAdminView;
import bd.dms.world.dto.CampAdminView;
import bd.dms.world.dto.DisasterAdminView;
import bd.dms.world.dto.GeometryHistoryView;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Full HTTP round-trip over the manual disaster-registration admin surface: every endpoint
 * under {@code /admin/disasters/**} succeeds for an ADMIN token and is forbidden for a
 * COORDINATOR token (the {@code /admin/**} wildcard in {@code SecurityConfig} is exercised for
 * real here, not assumed), an unknown disaster id on a nested endpoint is a 400 not a 500, a
 * partial update leaves untouched fields alone, and geometry history accumulates correctly
 * across a create+edit sequence.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DisasterAdminControllerIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private String loginAs(String username) {
        AuthResponse login = rest.postForObject(
                "/auth/login", new LoginRequest(username, "relief2026"), AuthResponse.class);
        return login.accessToken();
    }

    private HttpHeaders authHeaders(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAs(username));
        return headers;
    }

    private static final String POLYGON = """
            {"type":"Polygon","coordinates":[[[90.0,24.0],[90.1,24.0],[90.1,24.1],[90.0,24.1],[90.0,24.0]]]}""";
    private static final String POLYGON_2 = """
            {"type":"Polygon","coordinates":[[[91.0,25.0],[91.1,25.0],[91.1,25.1],[91.0,25.1],[91.0,25.0]]]}""";

    private ResponseEntity<DisasterAdminView> createDisaster(String code, HttpHeaders headers) {
        Map<String, String> body = Map.of(
                "code", code, "type", "FLOOD", "nameEn", "Test Flood", "nameBn", "টেস্ট বন্যা",
                "geometry", POLYGON);
        return rest.exchange("/admin/disasters", POST, new HttpEntity<>(body, headers), DisasterAdminView.class);
    }

    @Test
    void adminCanCreateUpdateAndCloseADisaster() {
        HttpHeaders admin = authHeaders("admin");

        ResponseEntity<DisasterAdminView> created = createDisaster("adm-flood-1", admin);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        DisasterAdminView disaster = created.getBody();
        assertThat(disaster.code()).isEqualTo("adm-flood-1");
        assertThat(disaster.status()).isEqualTo("ACTIVE");
        assertThat(disaster.geometry()).isNotNull();

        // Partial update: only nameEn changes, geometry stays exactly what it was.
        ResponseEntity<DisasterAdminView> updated = rest.exchange(
                "/admin/disasters/" + disaster.id(), PUT,
                new HttpEntity<>(Map.of("nameEn", "Renamed Flood"), admin), DisasterAdminView.class);
        assertThat(updated.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(updated.getBody().nameEn()).isEqualTo("Renamed Flood");
        assertThat(updated.getBody().nameBn()).isEqualTo(disaster.nameBn());
        assertThat(updated.getBody().geometry()).isEqualTo(disaster.geometry());

        ResponseEntity<DisasterAdminView> closed = rest.exchange(
                "/admin/disasters/" + disaster.id() + "/close", POST,
                new HttpEntity<>(null, admin), DisasterAdminView.class);
        assertThat(closed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(closed.getBody().status()).isEqualTo("CLOSED");
    }

    @Test
    void adminCanCreateAnAffectedAreaAndACamp() {
        HttpHeaders admin = authHeaders("admin");
        Long disasterId = createDisaster("adm-flood-2", admin).getBody().id();

        ResponseEntity<AffectedAreaAdminView> area = rest.exchange(
                "/admin/disasters/" + disasterId + "/affected-areas", POST,
                new HttpEntity<>(Map.of("nameEn", "Char Belt", "nameBn", "চর বেল্ট", "geometry", POLYGON), admin),
                AffectedAreaAdminView.class);
        assertThat(area.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(area.getBody().disasterId()).isEqualTo(disasterId);
        assertThat(area.getBody().geometry()).isNotNull();

        ResponseEntity<CampAdminView> camp = rest.exchange(
                "/admin/disasters/" + disasterId + "/camps", POST,
                new HttpEntity<>(Map.of(
                        "code", "adm-camp-1", "nameEn", "New Camp", "nameBn", "নতুন ক্যাম্প",
                        "lat", 24.05, "lng", 90.05, "capacity", 500, "initialPopulation", 100), admin),
                CampAdminView.class);
        assertThat(camp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(camp.getBody().code()).isEqualTo("adm-camp-1");
        assertThat(camp.getBody().population()).isEqualTo(100);
        assertThat(camp.getBody().status()).isEqualTo("OPEN");
    }

    @Test
    void geometryHistoryAccumulatesAcrossCreateAndEdit() {
        HttpHeaders admin = authHeaders("admin");
        DisasterAdminView disaster = createDisaster("adm-flood-3", admin).getBody();

        rest.exchange(
                "/admin/disasters/" + disaster.id(), PUT,
                new HttpEntity<>(Map.of("geometry", POLYGON_2), admin), DisasterAdminView.class);

        ResponseEntity<GeometryHistoryView[]> history = rest.exchange(
                "/admin/disasters/" + disaster.id() + "/geometry-history", GET,
                new HttpEntity<>(admin), GeometryHistoryView[].class);
        assertThat(history.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(history.getBody()).hasSize(2);
        // A JSON-null field deserializes to Jackson's NullNode, not a Java null reference.
        assertThat(history.getBody()[0].previousGeometry().isNull()).isTrue();
        assertThat(history.getBody()[0].newGeometry()).isEqualTo(disaster.geometry());
        assertThat(history.getBody()[1].previousGeometry()).isEqualTo(disaster.geometry());

        ResponseEntity<AffectedAreaAdminView> area = rest.exchange(
                "/admin/disasters/" + disaster.id() + "/affected-areas", POST,
                new HttpEntity<>(Map.of("nameEn", "Area", "nameBn", "এলাকা", "geometry", POLYGON), admin),
                AffectedAreaAdminView.class);

        ResponseEntity<GeometryHistoryView[]> areaHistory = rest.exchange(
                "/admin/affected-areas/" + area.getBody().id() + "/geometry-history", GET,
                new HttpEntity<>(admin), GeometryHistoryView[].class);
        assertThat(areaHistory.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(areaHistory.getBody()).hasSize(1);
        assertThat(areaHistory.getBody()[0].previousGeometry().isNull()).isTrue();
    }

    @Test
    void unknownDisasterIdOnNestedEndpointsIsBadRequestNotServerError() {
        HttpHeaders admin = authHeaders("admin");

        ResponseEntity<String> updateMissing = rest.exchange(
                "/admin/disasters/999999", PUT, new HttpEntity<>(Map.of("nameEn", "X"), admin), String.class);
        assertThat(updateMissing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> closeMissing = rest.exchange(
                "/admin/disasters/999999/close", POST, new HttpEntity<>(null, admin), String.class);
        assertThat(closeMissing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> areaMissing = rest.exchange(
                "/admin/disasters/999999/affected-areas", POST,
                new HttpEntity<>(Map.of("nameEn", "A", "nameBn", "A", "geometry", POLYGON), admin), String.class);
        assertThat(areaMissing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> campMissing = rest.exchange(
                "/admin/disasters/999999/camps", POST,
                new HttpEntity<>(Map.of(
                        "code", "orphan-camp", "nameEn", "A", "nameBn", "A",
                        "lat", 1.0, "lng", 1.0, "capacity", 10, "initialPopulation", 0), admin),
                String.class);
        assertThat(campMissing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDisasterValidationFailureIsBadRequest() {
        HttpHeaders admin = authHeaders("admin");
        ResponseEntity<String> response = rest.exchange(
                "/admin/disasters", POST,
                new HttpEntity<>(Map.of("code", "", "type", "FLOOD", "nameEn", "", "nameBn", "", "geometry", ""), admin),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void nonAdminIsForbiddenOnEveryEndpoint() {
        HttpHeaders admin = authHeaders("admin");
        DisasterAdminView disaster = createDisaster("adm-flood-4", admin).getBody();
        HttpHeaders coordinator = authHeaders("coordinator");

        assertThat(createDisaster("adm-flood-should-fail", coordinator).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(rest.exchange(
                        "/admin/disasters/" + disaster.id(), PUT,
                        new HttpEntity<>(Map.of("nameEn", "X"), coordinator), String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(rest.exchange(
                        "/admin/disasters/" + disaster.id() + "/close", POST,
                        new HttpEntity<>(null, coordinator), String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(rest.exchange(
                        "/admin/disasters/" + disaster.id() + "/affected-areas", POST,
                        new HttpEntity<>(Map.of("nameEn", "A", "nameBn", "A", "geometry", POLYGON), coordinator),
                        String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(rest.exchange(
                        "/admin/disasters/" + disaster.id() + "/camps", POST,
                        new HttpEntity<>(Map.of(
                                "code", "should-not-be-created", "nameEn", "A", "nameBn", "A",
                                "lat", 1.0, "lng", 1.0, "capacity", 10, "initialPopulation", 0), coordinator),
                        String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(rest.exchange(
                        "/admin/disasters/" + disaster.id() + "/geometry-history", GET,
                        new HttpEntity<>(coordinator), String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
