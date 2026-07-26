package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;

import bd.dms.allocation.AllocationDecision;
import bd.dms.allocation.AllocationDecisionRepository;
import bd.dms.anomaly.AnomalyDetectorType;
import bd.dms.anomaly.AnomalyFlag;
import bd.dms.anomaly.AnomalyFlagRepository;
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
class AnomalyDemoTriggerIntegrationTest {

    private static final String SHORTAGE_CAMP_CODE = "jam-fulchhari";
    private static final String SURPLUS_CAMP_CODE = "jam-sundarganj";
    private static final List<String> RESOURCE_TYPES = List.of("WATER", "FOOD", "MEDICAL");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AllocationDecisionRepository allocations;

    @Autowired
    private AnomalyFlagRepository flags;

    @Autowired
    private CampRepository camps;

    @Autowired
    private CampResourceObservationRepository observations;

    private String loginAs(String username) {
        AuthResponse login = rest.postForObject("/auth/login",
                new LoginRequest(username, "relief2026"), AuthResponse.class);
        return login.accessToken();
    }

    private String demoUrl(Long shortageCampId, Long surplusCampId) {
        return "/anomalies/demo/burst/" + shortageCampId + "/" + surplusCampId;
    }

    @Test
    void coordinatorTriggeringTheDemoProducesThreeAllocationsAndOneBurstFlag() {
        Long shortageCampId = camps.findByCode(SHORTAGE_CAMP_CODE).orElseThrow().getId();
        Long surplusCampId = camps.findByCode(SURPLUS_CAMP_CODE).orElseThrow().getId();
        allocations.deleteAll();
        flags.deleteAll();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAs("coordinator"));

        ResponseEntity<Void> response = rest.exchange(
                demoUrl(shortageCampId, surplusCampId), POST, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        List<AllocationDecision> decisions = allocations.findAll();
        assertThat(decisions).hasSize(3);
        assertThat(decisions.stream().map(AllocationDecision::getResourceType).distinct().toList())
                .containsExactlyInAnyOrderElementsOf(RESOURCE_TYPES);

        List<AnomalyFlag> burstFlags = flags.findAll().stream()
                .filter(f -> f.getDetectorType() == AnomalyDetectorType.ALLOCATION_BURST)
                .toList();
        assertThat(burstFlags).hasSize(1);
    }

    @Test
    void theSyntheticObservationsDoNotOutliveTheRequest() {
        Long shortageCampId = camps.findByCode(SHORTAGE_CAMP_CODE).orElseThrow().getId();
        Long surplusCampId = camps.findByCode(SURPLUS_CAMP_CODE).orElseThrow().getId();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAs("coordinator"));

        ResponseEntity<Void> response = rest.exchange(
                demoUrl(shortageCampId, surplusCampId), POST, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        for (String resourceType : RESOURCE_TYPES) {
            assertThat(observations.findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(
                            shortageCampId, resourceType, 0))
                    .isEmpty();
            assertThat(observations.findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(
                            surplusCampId, resourceType, 0))
                    .isEmpty();
        }
    }

    @Test
    void nonOversightRolesAreRefusedOnDemo() {
        Long shortageCampId = camps.findByCode(SHORTAGE_CAMP_CODE).orElseThrow().getId();
        Long surplusCampId = camps.findByCode(SURPLUS_CAMP_CODE).orElseThrow().getId();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAs("camp_manager"));

        ResponseEntity<Void> response = rest.exchange(
                demoUrl(shortageCampId, surplusCampId), POST, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void nonOversightRolesAreRefusedOnList() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAs("camp_manager"));

        ResponseEntity<Object> response = rest.exchange(
                "/anomalies", org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), Object.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void nonOversightRolesAreRefusedOnReview() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAs("camp_manager"));
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String body = "{\"toStatus\":\"DISMISSED\",\"note\":\"nope\"}";

        ResponseEntity<Object> response = rest.exchange(
                "/anomalies/1/review", POST, new HttpEntity<>(body, headers), Object.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
