package bd.dms.api;

import bd.dms.funds.FundLedgerService;
import bd.dms.funds.dto.DonationView;
import bd.dms.funds.dto.DonorImpactView;
import bd.dms.funds.dto.FundsReport;
import bd.dms.funds.dto.ProcurementView;
import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The money-model surface: a donor gives to a disaster, a coordinator/admin procures against the
 * resulting ledger balance, and both audiences read back a shape scoped to them — the donor their
 * own aggregated Donation → Camp impact, the coordinator/admin the full unaccounted-funds report.
 * Role checks live in {@link FundLedgerService}, matching every other module's no-@PreAuthorize
 * convention; this controller only resolves the caller and shapes responses.
 */
@RestController
@RequestMapping("/funds")
public class FundsController {

    public record DonateRequest(@NotNull Long disasterId, @NotNull BigDecimal amount) {}

    public record ProcureRequest(
            @NotNull Long disasterId, @NotNull Long campId, @NotNull String resourceType, @NotNull BigDecimal amount) {}

    private final FundLedgerService ledger;
    private final UserRepository users;

    public FundsController(FundLedgerService ledger, UserRepository users) {
        this.ledger = ledger;
        this.users = users;
    }

    @PostMapping("/donations")
    public DonationView donate(@Valid @RequestBody DonateRequest request, Authentication authentication) {
        return ledger.donate(actor(authentication), request.disasterId(), request.amount());
    }

    @GetMapping("/donations/mine")
    public List<DonationView> myDonations(Authentication authentication) {
        return ledger.myDonations(actor(authentication).getId());
    }

    @GetMapping("/donations/mine/impact")
    public DonorImpactView myImpact(Authentication authentication) {
        return ledger.donorImpact(actor(authentication).getId());
    }

    @PostMapping("/procurements")
    public ProcurementView procure(@Valid @RequestBody ProcureRequest request, Authentication authentication) {
        return ledger.procure(
                actor(authentication), request.disasterId(), request.campId(), request.resourceType(), request.amount());
    }

    @GetMapping("/procurements/{disasterId}")
    public List<ProcurementView> procurements(@PathVariable Long disasterId, Authentication authentication) {
        return ledger.procurementsFor(actor(authentication), disasterId);
    }

    @GetMapping("/report")
    public FundsReport report(Authentication authentication) {
        return ledger.report(actor(authentication));
    }

    private AppUser actor(Authentication authentication) {
        return users.findByUsername(authentication.getName()).orElseThrow();
    }
}
