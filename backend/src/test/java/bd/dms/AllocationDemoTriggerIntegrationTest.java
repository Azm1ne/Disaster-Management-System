package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;

import bd.dms.allocation.AllocationDecision;
import bd.dms.allocation.AllocationDecisionRepository;
import bd.dms.allocation.AllocationStatus;
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
class AllocationDemoTriggerIntegrationTest {

    private static final String SHORTAGE_CAMP_CODE = "jam-fulchhari";
    private static final String SURPLUS_CAMP_CODE = "jam-sundarganj";
    private static final String RESOURCE_TYPE = "MEDICAL";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AllocationDecisionRepository allocations;

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
        return "/allocations/demo/" + shortageCampId + "/" + surplusCampId + "/" + RESOURCE_TYPE;
    }

    @Test
    void coordinatorTriggeringTheDemoProducesARealAllocationRecommendation() {
        Long shortageCampId = camps.findByCode(SHORTAGE_CAMP_CODE).orElseThrow().getId();
        Long surplusCampId = camps.findByCode(SURPLUS_CAMP_CODE).orElseThrow().getId();
        allocations.deleteAll();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAs("coordinator"));

        ResponseEntity<Void> response = rest.exchange(
                demoUrl(shortageCampId, surplusCampId), POST, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        List<AllocationDecision> recommendations = allocations.findAll().stream()
                .filter(d -> d.getResourceType().equals(RESOURCE_TYPE)
                        && d.getSourceCampId().equals(surplusCampId)
                        && d.getTargetCampId().equals(shortageCampId)
                        && d.getStatus() == AllocationStatus.RECOMMENDED)
                .toList();
        assertThat(recommendations).hasSize(1);
        assertThat(recommendations.get(0).getRecommendedQuantity().signum()).isPositive();
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
        assertThat(observations.findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(
                        shortageCampId, RESOURCE_TYPE, 0))
                .isEmpty();
        assertThat(observations.findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(
                        surplusCampId, RESOURCE_TYPE, 0))
                .isEmpty();
    }

    @Test
    void nonOversightRolesAreRefused() {
        Long shortageCampId = camps.findByCode(SHORTAGE_CAMP_CODE).orElseThrow().getId();
        Long surplusCampId = camps.findByCode(SURPLUS_CAMP_CODE).orElseThrow().getId();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAs("camp_manager"));

        ResponseEntity<Void> response = rest.exchange(
                demoUrl(shortageCampId, surplusCampId), POST, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anUnknownResourceTypeIsRejected() {
        Long shortageCampId = camps.findByCode(SHORTAGE_CAMP_CODE).orElseThrow().getId();
        Long surplusCampId = camps.findByCode(SURPLUS_CAMP_CODE).orElseThrow().getId();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAs("coordinator"));

        ResponseEntity<Void> response = rest.exchange(
                "/allocations/demo/" + shortageCampId + "/" + surplusCampId + "/BANANA",
                POST, new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
