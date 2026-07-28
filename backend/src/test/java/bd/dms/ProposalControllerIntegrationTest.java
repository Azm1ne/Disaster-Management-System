package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import bd.dms.proposal.ProposalStatus;
import bd.dms.proposal.ProposalType;
import bd.dms.proposal.dto.ProposalView;
import bd.dms.world.AffectedArea;
import bd.dms.world.AffectedAreaRepository;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import bd.dms.world.CampResource;
import bd.dms.world.CampResourceRepository;
import bd.dms.world.Disaster;
import bd.dms.world.DisasterRepository;
import bd.dms.world.GeometryHistoryRepository;
import bd.dms.world.GeometrySubjectType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Full HTTP round-trip over the proposal/approval inbox: a coordinator can propose any of the
 * 5 proposal types (COORDINATOR-only), a central authority reviews them (CENTRAL_AUTHORITY-only)
 * and approval actually reaches {@code DisasterAdminService} (verified via the repositories, not
 * just the response body) while rejection leaves the world untouched, and acting twice on an
 * already-resolved proposal is rejected rather than silently accepted or double-applied.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProposalControllerIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private DisasterRepository disasters;

    @Autowired
    private GeometryHistoryRepository geometryHistory;

    @Autowired
    private AffectedAreaRepository affectedAreas;

    @Autowired
    private CampRepository camps;

    @Autowired
    private CampResourceRepository campResources;

    private static final String POLYGON = """
            {"type":"Polygon","coordinates":[[[90.0,24.0],[90.1,24.0],[90.1,24.1],[90.0,24.1],[90.0,24.0]]]}""";

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

    private ResponseEntity<ProposalView> proposeDisasterCreate(String code, HttpHeaders headers) {
        Map<String, Object> body = Map.of(
                "proposalType", "DISASTER_CREATE",
                "payload", Map.of(
                        "code", code, "type", "FLOOD", "nameEn", "Proposed Flood", "nameBn", "প্রস্তাবিত বন্যা",
                        "geometry", POLYGON));
        return rest.exchange("/proposals", POST, new HttpEntity<>(body, headers), ProposalView.class);
    }

    private ResponseEntity<String> proposeDisasterCreateRaw(String code, HttpHeaders headers) {
        Map<String, Object> body = Map.of(
                "proposalType", "DISASTER_CREATE",
                "payload", Map.of(
                        "code", code, "type", "FLOOD", "nameEn", "Proposed Flood", "nameBn", "প্রস্তাবিত বন্যা",
                        "geometry", POLYGON));
        return rest.exchange("/proposals", POST, new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void coordinatorCanProposeADisasterCreateAndACampCreate() {
        HttpHeaders coordinator = authHeaders("coordinator");

        ResponseEntity<ProposalView> disasterProposal = proposeDisasterCreate("prop-flood-1", coordinator);
        assertThat(disasterProposal.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(disasterProposal.getBody().proposalType()).isEqualTo(ProposalType.DISASTER_CREATE);
        assertThat(disasterProposal.getBody().targetDisasterId()).isNull();
        assertThat(disasterProposal.getBody().status()).isEqualTo(ProposalStatus.PENDING);

        // Camp proposals target an existing disaster, created directly via the admin path here
        // just to have a real target id — the proposal path itself is what's under test.
        Long disasterId = disasters.findAll().get(0).getId();
        Map<String, Object> campBody = Map.of(
                "proposalType", "CAMP_CREATE",
                "targetDisasterId", disasterId,
                "payload", Map.of(
                        "code", "prop-camp-1", "nameEn", "Proposed Camp", "nameBn", "প্রস্তাবিত ক্যাম্প",
                        "lat", 24.1, "lng", 90.1, "capacity", 300, "initialPopulation", 50));
        ResponseEntity<ProposalView> campProposal = rest.exchange(
                "/proposals", POST, new HttpEntity<>(campBody, coordinator), ProposalView.class);
        assertThat(campProposal.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(campProposal.getBody().targetDisasterId()).isEqualTo(disasterId);
    }

    @Test
    void nonCoordinatorIsForbiddenOnPropose() {
        HttpHeaders admin = authHeaders("admin");
        assertThat(proposeDisasterCreateRaw("prop-flood-forbidden", admin).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void centralAuthorityApprovingADisasterCreateActuallyCreatesTheDisasterWithHistory() {
        HttpHeaders coordinator = authHeaders("coordinator");
        HttpHeaders centralAuthority = authHeaders("central_authority");

        Long proposalId = proposeDisasterCreate("prop-flood-approve", coordinator).getBody().id();

        ResponseEntity<ProposalView> approved = rest.exchange(
                "/proposals/" + proposalId + "/approve", POST,
                new HttpEntity<>(Map.of("reviewNote", "looks good"), centralAuthority), ProposalView.class);
        assertThat(approved.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(approved.getBody().status()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(approved.getBody().reviewNote()).isEqualTo("looks good");

        Optional<Disaster> created = disasters.findAll().stream()
                .filter(d -> "prop-flood-approve".equals(d.getCode()))
                .findFirst();
        assertThat(created).isPresent();
        assertThat(created.get().getStatus()).isEqualTo("ACTIVE");

        List<?> history = geometryHistory.findBySubjectTypeAndSubjectIdOrderByCreatedAtAscIdAsc(
                GeometrySubjectType.DISASTER, created.get().getId());
        assertThat(history).hasSize(1);
    }

    @Test
    void centralAuthorityRejectingLeavesWorldUntouched() {
        HttpHeaders coordinator = authHeaders("coordinator");
        HttpHeaders centralAuthority = authHeaders("central_authority");

        Long proposalId = proposeDisasterCreate("prop-flood-reject", coordinator).getBody().id();

        ResponseEntity<ProposalView> rejected = rest.exchange(
                "/proposals/" + proposalId + "/reject", POST,
                new HttpEntity<>(Map.of("reviewNote", "not enough detail"), centralAuthority), ProposalView.class);
        assertThat(rejected.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(rejected.getBody().status()).isEqualTo(ProposalStatus.REJECTED);

        assertThat(disasters.findAll().stream().anyMatch(d -> "prop-flood-reject".equals(d.getCode()))).isFalse();
    }

    @Test
    void nonCentralAuthorityIsForbiddenOnReviewAndList() {
        HttpHeaders coordinator = authHeaders("coordinator");
        HttpHeaders admin = authHeaders("admin");

        Long proposalId = proposeDisasterCreate("prop-flood-forbidden-review", coordinator).getBody().id();

        assertThat(rest.exchange("/proposals", GET, new HttpEntity<>(coordinator), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange(
                        "/proposals/" + proposalId + "/approve", POST, new HttpEntity<>(null, coordinator), String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange(
                        "/proposals/" + proposalId + "/reject", POST, new HttpEntity<>(null, admin), String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void actingTwiceOnAResolvedProposalIsRejected() {
        HttpHeaders coordinator = authHeaders("coordinator");
        HttpHeaders centralAuthority = authHeaders("central_authority");

        Long proposalId = proposeDisasterCreate("prop-flood-idempotency", coordinator).getBody().id();

        ResponseEntity<ProposalView> firstApproval = rest.exchange(
                "/proposals/" + proposalId + "/approve", POST, new HttpEntity<>(null, centralAuthority), ProposalView.class);
        assertThat(firstApproval.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> secondApproval = rest.exchange(
                "/proposals/" + proposalId + "/approve", POST, new HttpEntity<>(null, centralAuthority), String.class);
        assertThat(secondApproval.getStatusCode().is5xxServerError()).isTrue();

        ResponseEntity<String> rejectAfterApprove = rest.exchange(
                "/proposals/" + proposalId + "/reject", POST, new HttpEntity<>(null, centralAuthority), String.class);
        assertThat(rejectAfterApprove.getStatusCode().is5xxServerError()).isTrue();

        // Only one disaster was ever created for this code, not two.
        assertThat(disasters.findAll().stream().filter(d -> "prop-flood-idempotency".equals(d.getCode())).count())
                .isEqualTo(1);
    }

    private ResponseEntity<ProposalView> propose(Map<String, Object> body, HttpHeaders headers) {
        return rest.exchange("/proposals", POST, new HttpEntity<>(body, headers), ProposalView.class);
    }

    private ResponseEntity<ProposalView> approve(Long proposalId, HttpHeaders centralAuthority) {
        return rest.exchange(
                "/proposals/" + proposalId + "/approve", POST, new HttpEntity<>(null, centralAuthority), ProposalView.class);
    }

    private ResponseEntity<String> approveRaw(Long proposalId, HttpHeaders centralAuthority) {
        return rest.exchange(
                "/proposals/" + proposalId + "/approve", POST, new HttpEntity<>(null, centralAuthority), String.class);
    }

    @Test
    void approvingAnAffectedAreaCreateProposalCreatesTheAreaWithHistory() {
        HttpHeaders coordinator = authHeaders("coordinator");
        HttpHeaders centralAuthority = authHeaders("central_authority");
        Long disasterId = disasters.findAll().get(0).getId();

        Map<String, Object> body = Map.of(
                "proposalType", "AFFECTED_AREA_CREATE",
                "targetDisasterId", disasterId,
                "payload", Map.of("nameEn", "Prop Area", "nameBn", "প্রস্তাবিত এলাকা", "geometry", POLYGON));
        Long proposalId = propose(body, coordinator).getBody().id();

        ResponseEntity<ProposalView> approved = approve(proposalId, centralAuthority);
        assertThat(approved.getStatusCode().is2xxSuccessful()).isTrue();

        List<AffectedArea> areas = affectedAreas.findByDisasterId(disasterId).stream()
                .filter(a -> "Prop Area".equals(a.getNameEn()))
                .toList();
        assertThat(areas).hasSize(1);

        List<?> history = geometryHistory.findBySubjectTypeAndSubjectIdOrderByCreatedAtAscIdAsc(
                GeometrySubjectType.AFFECTED_AREA, areas.get(0).getId());
        assertThat(history).hasSize(1);
    }

    @Test
    void approvingACampCreateProposalCreatesTheCampAndSeedsResources() {
        HttpHeaders coordinator = authHeaders("coordinator");
        HttpHeaders centralAuthority = authHeaders("central_authority");
        Long disasterId = disasters.findAll().get(0).getId();

        Map<String, Object> body = Map.of(
                "proposalType", "CAMP_CREATE",
                "targetDisasterId", disasterId,
                "payload", Map.of(
                        "code", "prop-camp-approved", "nameEn", "Approved Camp", "nameBn", "অনুমোদিত ক্যাম্প",
                        "lat", 24.25, "lng", 90.35, "capacity", 400, "initialPopulation", 80));
        Long proposalId = propose(body, coordinator).getBody().id();

        ResponseEntity<ProposalView> approved = approve(proposalId, centralAuthority);
        assertThat(approved.getStatusCode().is2xxSuccessful()).isTrue();

        Camp camp = camps.findByCode("prop-camp-approved").orElseThrow();
        assertThat(camp.getLat()).isEqualTo(24.25);
        assertThat(camp.getLng()).isEqualTo(90.35);
        assertThat(camp.getCapacity()).isEqualTo(400);
        assertThat(camp.getPopulation()).isEqualTo(80);

        List<CampResource> resources = campResources.findByCampId(camp.getId());
        assertThat(resources).extracting(CampResource::getResourceType)
                .containsExactlyInAnyOrder("WATER", "FOOD", "MEDICAL");
    }

    @Test
    void approvingADisasterUpdateProposalActuallyChangesTheDisaster() {
        HttpHeaders coordinator = authHeaders("coordinator");
        HttpHeaders centralAuthority = authHeaders("central_authority");
        Disaster target = disasters.findAll().get(0);
        String originalNameBn = target.getNameBn();

        Map<String, Object> body = Map.of(
                "proposalType", "DISASTER_UPDATE",
                "targetDisasterId", target.getId(),
                "payload", Map.of("nameEn", "Renamed Via Proposal"));
        Long proposalId = propose(body, coordinator).getBody().id();

        ResponseEntity<ProposalView> approved = approve(proposalId, centralAuthority);
        assertThat(approved.getStatusCode().is2xxSuccessful()).isTrue();

        Disaster reloaded = disasters.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getNameEn()).isEqualTo("Renamed Via Proposal");
        assertThat(reloaded.getNameBn()).isEqualTo(originalNameBn);
    }

    @Test
    void approvingADisasterCloseProposalActuallyClosesTheDisaster() {
        HttpHeaders coordinator = authHeaders("coordinator");
        HttpHeaders centralAuthority = authHeaders("central_authority");

        // Propose and approve a create first, so there's a real disaster to close.
        Long createProposalId = proposeDisasterCreate("prop-flood-to-close", coordinator).getBody().id();
        approve(createProposalId, centralAuthority);
        Long targetDisasterId = disasters.findAll().stream()
                .filter(d -> "prop-flood-to-close".equals(d.getCode()))
                .findFirst().orElseThrow().getId();

        Map<String, Object> closeBody = Map.of(
                "proposalType", "DISASTER_CLOSE",
                "targetDisasterId", targetDisasterId,
                "payload", Map.of());
        Long closeProposalId = propose(closeBody, coordinator).getBody().id();

        ResponseEntity<ProposalView> approvedClose = approve(closeProposalId, centralAuthority);
        assertThat(approvedClose.getStatusCode().is2xxSuccessful()).isTrue();

        Disaster reloaded = disasters.findById(targetDisasterId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void approvingAProposalWithAnInvalidPayloadIsRejectedNotSilentlyPersisted() {
        HttpHeaders coordinator = authHeaders("coordinator");
        HttpHeaders centralAuthority = authHeaders("central_authority");
        Long disasterId = disasters.findAll().get(0).getId();

        // Blank nameEn and negative capacity both violate CreateCampRequest's constraints, but
        // Bean Validation only runs automatically on @Valid @RequestBody, not on a payload
        // deserialized by hand inside ProposalService — this proves that gap is now closed.
        Map<String, Object> body = Map.of(
                "proposalType", "CAMP_CREATE",
                "targetDisasterId", disasterId,
                "payload", Map.of(
                        "code", "prop-camp-invalid", "nameEn", "", "nameBn", "X",
                        "lat", 24.0, "lng", 90.0, "capacity", -5, "initialPopulation", 0));
        Long proposalId = propose(body, coordinator).getBody().id();

        ResponseEntity<String> approved = approveRaw(proposalId, centralAuthority);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(camps.findByCode("prop-camp-invalid")).isEmpty();
    }

    @Test
    void targetDisasterIdValidationIsEnforcedAtProposeTime() {
        HttpHeaders coordinator = authHeaders("coordinator");
        Long disasterId = disasters.findAll().get(0).getId();

        // DISASTER_CREATE must not carry a targetDisasterId.
        Map<String, Object> disasterCreateWithTarget = Map.of(
                "proposalType", "DISASTER_CREATE",
                "targetDisasterId", disasterId,
                "payload", Map.of(
                        "code", "prop-should-fail", "type", "FLOOD", "nameEn", "X", "nameBn", "X", "geometry", POLYGON));
        assertThat(rest.exchange("/proposals", POST, new HttpEntity<>(disasterCreateWithTarget, coordinator), String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // AFFECTED_AREA_CREATE requires a targetDisasterId.
        Map<String, Object> affectedAreaWithoutTarget = Map.of(
                "proposalType", "AFFECTED_AREA_CREATE",
                "payload", Map.of("nameEn", "X", "nameBn", "X", "geometry", POLYGON));
        assertThat(rest.exchange("/proposals", POST, new HttpEntity<>(affectedAreaWithoutTarget, coordinator), String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // CAMP_CREATE requires a targetDisasterId.
        Map<String, Object> campWithoutTarget = Map.of(
                "proposalType", "CAMP_CREATE",
                "payload", Map.of(
                        "code", "prop-camp-should-fail", "nameEn", "X", "nameBn", "X",
                        "lat", 1.0, "lng", 1.0, "capacity", 10, "initialPopulation", 0));
        assertThat(rest.exchange("/proposals", POST, new HttpEntity<>(campWithoutTarget, coordinator), String.class)
                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
