package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.dms.funds.DonationRepository;
import bd.dms.funds.FundLedgerService;
import bd.dms.funds.ProcurementRepository;
import bd.dms.funds.dto.DonationView;
import bd.dms.funds.dto.DonorImpactView;
import bd.dms.funds.dto.FundsReport;
import bd.dms.funds.dto.ProcurementView;
import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import bd.dms.world.Disaster;
import bd.dms.world.DisasterRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

/**
 * The hard ledger invariant under a real sequence of donations/procurements: a disaster can
 * never procure more than it has been donated, checked fresh at every spend, and the
 * unaccounted-funds report and donor impact view always agree with the raw rows.
 */
@SpringBootTest
class FundLedgerServiceIntegrationTest {

    @Autowired
    private FundLedgerService ledger;

    @Autowired
    private DonationRepository donations;

    @Autowired
    private ProcurementRepository procurements;

    @Autowired
    private UserRepository users;

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

    private AppUser donor() {
        return users.findByUsername("donor").orElseThrow();
    }

    private AppUser coordinator() {
        return users.findByUsername("coordinator").orElseThrow();
    }

    private AppUser campManager() {
        return users.findByUsername("camp_manager").orElseThrow();
    }

    private Disaster jamunaFlood() {
        return disasters.findAll().stream()
                .filter(d -> d.getCode().equals("jamuna-flood-2024"))
                .findFirst()
                .orElseGet(() -> disasters.findAll().get(0));
    }

    private Camp aCampIn(Disaster disaster) {
        return camps.findByDisasterId(disaster.getId()).get(0);
    }

    @Test
    void aDonorCanDonateAndTheBalanceReflectsIt() {
        Disaster disaster = jamunaFlood();
        DonationView view = ledger.donate(donor(), disaster.getId(), BigDecimal.valueOf(1000));

        assertThat(view.amount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(ledger.balance(disaster.getId())).isEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    @Test
    void onlyADonorCanDonate() {
        Disaster disaster = jamunaFlood();
        assertThatThrownBy(() -> ledger.donate(coordinator(), disaster.getId(), BigDecimal.TEN))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void procurementWithinBalanceSucceedsAndDrawsDownTheLedger() {
        Disaster disaster = jamunaFlood();
        Camp camp = aCampIn(disaster);
        ledger.donate(donor(), disaster.getId(), BigDecimal.valueOf(1000));

        ProcurementView procurement =
                ledger.procure(coordinator(), disaster.getId(), camp.getId(), "WATER", BigDecimal.valueOf(500));

        assertThat(procurement.quantity()).isEqualByComparingTo(BigDecimal.valueOf(100)); // 500 / 5 per unit
        assertThat(ledger.balance(disaster.getId())).isEqualByComparingTo(BigDecimal.valueOf(500));
    }

    @Test
    void procuringMoreThanTheBalanceIsHardBlocked() {
        Disaster disaster = jamunaFlood();
        Camp camp = aCampIn(disaster);
        ledger.donate(donor(), disaster.getId(), BigDecimal.valueOf(100));

        assertThatThrownBy(() ->
                        ledger.procure(coordinator(), disaster.getId(), camp.getId(), "WATER", BigDecimal.valueOf(101)))
                .isInstanceOf(IllegalArgumentException.class);
        // The rejected spend must not have been persisted — balance is untouched.
        assertThat(ledger.balance(disaster.getId())).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void aSecondProcurementDrawingOnAnAlreadySpentBalanceIsBlocked() {
        Disaster disaster = jamunaFlood();
        Camp camp = aCampIn(disaster);
        ledger.donate(donor(), disaster.getId(), BigDecimal.valueOf(100));
        ledger.procure(coordinator(), disaster.getId(), camp.getId(), "WATER", BigDecimal.valueOf(80));

        assertThatThrownBy(() ->
                        ledger.procure(coordinator(), disaster.getId(), camp.getId(), "FOOD", BigDecimal.valueOf(21)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyCoordinatorOrAdminCanProcure() {
        Disaster disaster = jamunaFlood();
        Camp camp = aCampIn(disaster);
        ledger.donate(donor(), disaster.getId(), BigDecimal.valueOf(1000));

        assertThatThrownBy(() ->
                        ledger.procure(campManager(), disaster.getId(), camp.getId(), "WATER", BigDecimal.valueOf(10)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unaccountedFundsReportMatchesDonatedMinusProcuredForEveryDisaster() {
        Disaster disaster = jamunaFlood();
        Camp camp = aCampIn(disaster);
        ledger.donate(donor(), disaster.getId(), BigDecimal.valueOf(1000));
        ledger.procure(coordinator(), disaster.getId(), camp.getId(), "WATER", BigDecimal.valueOf(300));
        ledger.procure(coordinator(), disaster.getId(), camp.getId(), "FOOD", BigDecimal.valueOf(200));

        FundsReport report = ledger.report(coordinator());

        var line = report.disasters().stream()
                .filter(d -> d.disasterId().equals(disaster.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(line.donated()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(line.procured()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(line.unaccounted()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(line.unaccounted()).isEqualByComparingTo(line.donated().subtract(line.procured()));
    }

    @Test
    void onlyCoordinatorOrAdminCanViewTheReport() {
        assertThatThrownBy(() -> ledger.report(donor())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void donorImpactViewAggregatesAcrossCampsAndNeverExposesVictimData() {
        Disaster disaster = jamunaFlood();
        Camp campA = camps.findByDisasterId(disaster.getId()).get(0);
        Camp campB = camps.findByDisasterId(disaster.getId()).get(1);
        ledger.donate(donor(), disaster.getId(), BigDecimal.valueOf(1000));
        ledger.procure(coordinator(), disaster.getId(), campA.getId(), "WATER", BigDecimal.valueOf(200));
        ledger.procure(coordinator(), disaster.getId(), campB.getId(), "FOOD", BigDecimal.valueOf(100));

        DonorImpactView impact = ledger.donorImpact(donor().getId());

        assertThat(impact.totalDonatedByMe()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        var disasterImpact = impact.disasters().stream()
                .filter(d -> d.disasterId().equals(disaster.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(disasterImpact.donatedByMe()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(disasterImpact.camps()).hasSize(2);
        assertThat(disasterImpact.camps())
                .extracting(c -> c.campNameEn())
                .doesNotContainNull();
        // Nothing in this shape carries a family/victim field — CampImpact's record components
        // are exhaustively camp identity, resource type, and aggregate money/quantity.
        assertThat(bd.dms.funds.dto.CampImpact.class.getRecordComponents())
                .extracting(rc -> rc.getName())
                .containsExactlyInAnyOrder(
                        "campId", "campNameEn", "campNameBn", "resourceType", "amountProcured", "quantityProcured");
    }

    @Test
    void aDonorWithNoDonationsGetsAnEmptyImpactView() {
        DonorImpactView impact = ledger.donorImpact(donor().getId());
        assertThat(impact.disasters()).isEmpty();
        assertThat(impact.totalDonatedByMe()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
