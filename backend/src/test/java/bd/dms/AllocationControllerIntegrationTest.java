package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import bd.dms.allocation.AllocationDecision;
import bd.dms.allocation.AllocationDecisionRepository;
import bd.dms.allocation.AllocationStatus;
import bd.dms.allocation.AllocationTransitionRepository;
import bd.dms.allocation.dto.AllocationSummary;
import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import bd.dms.sim.SimulationEngine;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Full HTTP round-trip over the allocation queue surface. {@code engine.reset()} plus clearing
 * {@code allocation_transitions}/{@code allocation_decisions} in {@code @BeforeEach}/
 * {@code @AfterEach} mirrors {@code AllocationServiceIntegrationTest}'s convention: at tick 0
 * with a single observation the forecast rate is zero, so surplus equals the full seeded
 * quantity, and no leftover rows from another test class shrink the "available" surplus a test
 * here sees.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AllocationControllerIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AllocationDecisionRepository allocations;

    @Autowired
    private AllocationTransitionRepository transitions;

    @Autowired
    private CampRepository camps;

    @Autowired
    private SimulationEngine engine;

    @BeforeEach
    void resetWorld() {
        engine.reset();
        transitions.deleteAll();
        allocations.deleteAll();
    }

    @AfterEach
    void cleanUpWorld() {
        transitions.deleteAll();
        allocations.deleteAll();
        engine.reset();
    }

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

    private AllocationDecision freshRecommendation(BigDecimal quantity) {
        Camp source = camps.findByCode("jam-kurigram-sadar").orElseThrow();
        Camp target = camps.findAll().stream()
                .filter(c -> !c.getId().equals(source.getId()))
                .findFirst()
                .orElseThrow();
        return allocations.save(new AllocationDecision(
                "WATER", source.getId(), target.getId(), quantity, 0.5, 0.5, 0.5, 0.5, 0.5, 0));
    }

    @Test
    void coordinatorCanListAndApproveAnAllocation() {
        AllocationDecision decision = freshRecommendation(BigDecimal.ONE);
        HttpHeaders headers = authHeaders("coordinator");

        ResponseEntity<AllocationSummary[]> list = rest.exchange(
                "/allocations", GET, new HttpEntity<>(headers), AllocationSummary[].class);
        assertThat(list.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(list.getBody()).extracting(AllocationSummary::id).contains(decision.getId());

        ResponseEntity<AllocationSummary> approve = rest.exchange(
                "/allocations/" + decision.getId() + "/transition", POST,
                new HttpEntity<>(Map.of("toStatus", "APPROVED"), headers), AllocationSummary.class);
        assertThat(approve.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(approve.getBody().status()).isEqualTo(AllocationStatus.APPROVED);
    }

    @Test
    void campManagerGetsForbiddenOnTransitionEvenForTheirOwnCamp() {
        AllocationDecision decision = freshRecommendation(BigDecimal.ONE);
        HttpHeaders headers = authHeaders("camp_manager");

        ResponseEntity<String> response = rest.exchange(
                "/allocations/" + decision.getId() + "/transition", POST,
                new HttpEntity<>(Map.of("toStatus", "APPROVED"), headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void overAllocatingReturnsBadRequest() {
        AllocationDecision decision = freshRecommendation(BigDecimal.valueOf(1_000_000));
        HttpHeaders headers = authHeaders("coordinator");

        ResponseEntity<AllocationSummary> response = rest.exchange(
                "/allocations/" + decision.getId() + "/transition", POST,
                new HttpEntity<>(Map.of("toStatus", "APPROVED"), headers), AllocationSummary.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void modifyingWithAQuantityIsAccepted() {
        AllocationDecision decision = freshRecommendation(BigDecimal.TEN);
        HttpHeaders headers = authHeaders("coordinator");

        ResponseEntity<AllocationSummary> response = rest.exchange(
                "/allocations/" + decision.getId() + "/transition", POST,
                new HttpEntity<>(Map.of("toStatus", "MODIFIED", "quantity", 5), headers),
                AllocationSummary.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().status()).isEqualTo(AllocationStatus.MODIFIED);
        assertThat(response.getBody().decidedQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
    }
}
