package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import bd.dms.funds.DonationRepository;
import bd.dms.funds.ProcurementRepository;
import bd.dms.funds.dto.DonationView;
import bd.dms.funds.dto.DonorImpactView;
import bd.dms.funds.dto.FundsReport;
import bd.dms.funds.dto.ProcurementView;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import bd.dms.world.Disaster;
import bd.dms.world.DisasterRepository;
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

/** Full HTTP round-trip over the money-model surface: donate, procure, read the report and the
 * donor's own impact view, and confirm role boundaries return the right status codes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FundsControllerIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private DonationRepository donations;

    @Autowired
    private ProcurementRepository procurements;

    @Autowired
    private DisasterRepository disasters;

    @Autowired
    private CampRepository camps;

    @BeforeEach
    void cleanLedger() {
        procurements.deleteAll();
        donations.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        procurements.deleteAll();
        donations.deleteAll();
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

    private Disaster aDisaster() {
        return disasters.findAll().get(0);
    }

    private Camp aCampIn(Disaster disaster) {
        return camps.findByDisasterId(disaster.getId()).get(0);
    }

    @Test
    void donorCanDonateAndReadItBackAndImpactAppearsAfterProcurement() {
        Disaster disaster = aDisaster();
        Camp camp = aCampIn(disaster);
        HttpHeaders donorHeaders = authHeaders("donor");
        HttpHeaders coordinatorHeaders = authHeaders("coordinator");

        ResponseEntity<DonationView> donateResponse = rest.exchange(
                "/funds/donations", POST,
                new HttpEntity<>(Map.of("disasterId", disaster.getId(), "amount", 500), donorHeaders),
                DonationView.class);
        assertThat(donateResponse.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<DonationView[]> mine = rest.exchange(
                "/funds/donations/mine", GET, new HttpEntity<>(donorHeaders), DonationView[].class);
        assertThat(mine.getBody()).extracting(DonationView::disasterId).contains(disaster.getId());

        ResponseEntity<ProcurementView> procureResponse = rest.exchange(
                "/funds/procurements", POST,
                new HttpEntity<>(
                        Map.of("disasterId", disaster.getId(), "campId", camp.getId(), "resourceType", "WATER", "amount", 100),
                        coordinatorHeaders),
                ProcurementView.class);
        assertThat(procureResponse.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<DonorImpactView> impact = rest.exchange(
                "/funds/donations/mine/impact", GET, new HttpEntity<>(donorHeaders), DonorImpactView.class);
        assertThat(impact.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(impact.getBody().disasters())
                .anySatisfy(d -> assertThat(d.camps()).isNotEmpty());
    }

    @Test
    void nonDonorGetsForbiddenOnDonate() {
        Disaster disaster = aDisaster();
        HttpHeaders headers = authHeaders("coordinator");

        ResponseEntity<String> response = rest.exchange(
                "/funds/donations", POST,
                new HttpEntity<>(Map.of("disasterId", disaster.getId(), "amount", 100), headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void donorGetsForbiddenOnProcure() {
        Disaster disaster = aDisaster();
        Camp camp = aCampIn(disaster);
        HttpHeaders headers = authHeaders("donor");

        ResponseEntity<String> response = rest.exchange(
                "/funds/procurements", POST,
                new HttpEntity<>(
                        Map.of("disasterId", disaster.getId(), "campId", camp.getId(), "resourceType", "WATER", "amount", 10),
                        headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void procuringBeyondBalanceReturnsBadRequest() {
        Disaster disaster = aDisaster();
        Camp camp = aCampIn(disaster);
        HttpHeaders coordinatorHeaders = authHeaders("coordinator");
        HttpHeaders donorHeaders = authHeaders("donor");

        rest.exchange(
                "/funds/donations", POST,
                new HttpEntity<>(Map.of("disasterId", disaster.getId(), "amount", 50), donorHeaders), DonationView.class);

        ResponseEntity<String> response = rest.exchange(
                "/funds/procurements", POST,
                new HttpEntity<>(
                        Map.of("disasterId", disaster.getId(), "campId", camp.getId(), "resourceType", "WATER", "amount", 5000),
                        coordinatorHeaders),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void donorGetsForbiddenOnReport() {
        HttpHeaders headers = authHeaders("donor");
        ResponseEntity<String> response = rest.exchange("/funds/report", GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void coordinatorCanReadTheUnaccountedFundsReport() {
        Disaster disaster = aDisaster();
        HttpHeaders donorHeaders = authHeaders("donor");
        HttpHeaders coordinatorHeaders = authHeaders("coordinator");

        rest.exchange(
                "/funds/donations", POST,
                new HttpEntity<>(Map.of("disasterId", disaster.getId(), "amount", 250), donorHeaders), DonationView.class);

        ResponseEntity<FundsReport> response = rest.exchange(
                "/funds/report", GET, new HttpEntity<>(coordinatorHeaders), FundsReport.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().disasters())
                .anySatisfy(d -> assertThat(d.disasterId()).isEqualTo(disaster.getId()));
    }
}
