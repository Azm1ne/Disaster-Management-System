package bd.dms.funds;

import bd.dms.anomaly.DonationPatternDetector;
import bd.dms.funds.dto.CampImpact;
import bd.dms.funds.dto.DisasterFundSummary;
import bd.dms.funds.dto.DisasterImpact;
import bd.dms.funds.dto.DonationView;
import bd.dms.funds.dto.DonorImpactView;
import bd.dms.funds.dto.FundsReport;
import bd.dms.funds.dto.ProcurementView;
import bd.dms.user.AppUser;
import bd.dms.user.Role;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import bd.dms.world.Disaster;
import bd.dms.world.DisasterRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sole writer of the money model (mirrors {@code AllocationService} and {@code FamilyService}
 * for their own tables): donations accrue into a disaster-level fund ledger, and procurement
 * spends against it, notionally converting money into a resource quantity for one camp.
 * {@code donations}/{@code procurements} are their own append-only tables — this class never
 * touches {@code camps}/{@code camp_resources}, which stay {@code SimulationEngine}'s alone.
 *
 * <p>The hard ledger invariant — a disaster can never procure more than it has been donated — is
 * enforced here, fresh, at spend time: {@link #balance} recomputes {@code sum(donations) -
 * sum(procurements)} straight from the tables on every call, so there is no cached running total
 * that could drift out of sync with the rows that are its only source of truth.
 */
@Service
public class FundLedgerService {

    /** Notional taka-per-unit for turning a procurement amount into a resource quantity — this
     * is a modeled pipeline (no real payment/supplier processing), so the prices are fixed demo
     * constants, not a market lookup. */
    private static final Map<String, BigDecimal> UNIT_PRICE = Map.of(
            "WATER", BigDecimal.valueOf(5),
            "FOOD", BigDecimal.valueOf(20),
            "MEDICAL", BigDecimal.valueOf(100));

    private final DonationRepository donations;
    private final ProcurementRepository procurements;
    private final DisasterRepository disasters;
    private final CampRepository camps;
    private final DonationPatternDetector donationDetector;

    public FundLedgerService(
            DonationRepository donations,
            ProcurementRepository procurements,
            DisasterRepository disasters,
            CampRepository camps,
            DonationPatternDetector donationDetector) {
        this.donations = donations;
        this.procurements = procurements;
        this.disasters = disasters;
        this.camps = camps;
        this.donationDetector = donationDetector;
    }

    @Transactional
    public DonationView donate(AppUser actor, Long disasterId, BigDecimal amount) {
        if (actor.getRole() != Role.DONOR) {
            throw new AccessDeniedException("Only a Donor can make a donation");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Donation amount must be positive");
        }
        Disaster disaster = disasters.findById(disasterId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown disaster: " + disasterId));

        Donation saved = donations.save(new Donation(actor.getId(), disaster.getId(), amount));
        donationDetector.scanDonor(saved);
        return toView(saved, disaster);
    }

    /** The hard invariant: a procurement can never spend more than the disaster's current
     * balance, recomputed fresh (not from a cached total) at the moment of spend. */
    @Transactional
    public ProcurementView procure(
            AppUser actor, Long disasterId, Long campId, String resourceType, BigDecimal amount) {
        if (actor.getRole() != Role.COORDINATOR && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only Coordinator/Admin can procure");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Procurement amount must be positive");
        }
        BigDecimal unitPrice = UNIT_PRICE.get(resourceType);
        if (unitPrice == null) {
            throw new IllegalArgumentException("Unknown resource type: " + resourceType);
        }
        Disaster disaster = disasters.findById(disasterId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown disaster: " + disasterId));
        Camp camp = camps.findById(campId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown camp: " + campId));
        if (!camp.getDisasterId().equals(disaster.getId())) {
            throw new IllegalArgumentException("Camp " + campId + " does not belong to disaster " + disasterId);
        }

        BigDecimal available = balance(disasterId);
        if (amount.compareTo(available) > 0) {
            throw new IllegalArgumentException(
                    "Requested procurement " + amount + " exceeds available fund balance " + available
                            + " for disaster " + disasterId);
        }

        BigDecimal quantity = amount.divide(unitPrice, 2, RoundingMode.DOWN);
        Procurement saved = procurements.save(
                new Procurement(disaster.getId(), camp.getId(), resourceType, amount, unitPrice, quantity, actor.getId()));
        return toView(saved, camp);
    }

    /** {@code sum(donations) - sum(procurements)} for one disaster, recomputed from the rows
     * every time — the single source of truth for "how much of this disaster's ledger is still
     * unspent." */
    @Transactional(readOnly = true)
    public BigDecimal balance(Long disasterId) {
        return totalDonated(disasterId).subtract(totalProcured(disasterId));
    }

    private BigDecimal totalDonated(Long disasterId) {
        return donations.findByDisasterId(disasterId).stream()
                .map(Donation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalProcured(Long disasterId) {
        return procurements.findByDisasterId(disasterId).stream()
                .map(Procurement::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public List<DonationView> myDonations(Long donorUserId) {
        return donations.findByDonorUserIdOrderByCreatedAtDesc(donorUserId).stream()
                .map(d -> toView(d, disasters.findById(d.getDisasterId()).orElseThrow()))
                .toList();
    }

    /** The donor's own aggregated Donation → Camp chain: for every disaster they've given to,
     * their own running total plus that disaster's whole procurement fan-out across camps —
     * never a per-dollar trace, and never anything from the family/victim tables. */
    @Transactional(readOnly = true)
    public DonorImpactView donorImpact(Long donorUserId) {
        Map<Long, BigDecimal> myTotalsByDisaster = new LinkedHashMap<>();
        for (Donation d : donations.findByDonorUserIdOrderByCreatedAtDesc(donorUserId)) {
            myTotalsByDisaster.merge(d.getDisasterId(), d.getAmount(), BigDecimal::add);
        }

        List<DisasterImpact> impacts = myTotalsByDisaster.entrySet().stream()
                .map(entry -> disasterImpact(entry.getKey(), entry.getValue()))
                .toList();
        BigDecimal total = myTotalsByDisaster.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DonorImpactView(impacts, total);
    }

    private DisasterImpact disasterImpact(Long disasterId, BigDecimal donatedByMe) {
        Disaster disaster = disasters.findById(disasterId).orElseThrow();
        Map<Long, Camp> campById = camps.findByDisasterId(disasterId).stream()
                .collect(java.util.stream.Collectors.toMap(Camp::getId, c -> c));

        Map<String, BigDecimal[]> byCampResource = new LinkedHashMap<>(); // [amount, quantity]
        for (Procurement p : procurements.findByDisasterId(disasterId)) {
            String key = p.getCampId() + "|" + p.getResourceType();
            BigDecimal[] totals = byCampResource.computeIfAbsent(key, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            totals[0] = totals[0].add(p.getAmount());
            totals[1] = totals[1].add(p.getQuantity());
        }

        List<CampImpact> camps = byCampResource.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("\\|", 2);
                    Camp camp = campById.get(Long.valueOf(parts[0]));
                    return new CampImpact(
                            camp.getId(), camp.getNameEn(), camp.getNameBn(), parts[1],
                            entry.getValue()[0], entry.getValue()[1]);
                })
                .sorted(Comparator.comparing(CampImpact::campNameEn).thenComparing(CampImpact::resourceType))
                .toList();

        return new DisasterImpact(disaster.getId(), disaster.getNameEn(), disaster.getNameBn(), donatedByMe, camps);
    }

    /** The unaccounted-funds report: every disaster's money-in vs. aid-out (via procurement),
     * and the gap between them. Coordinator/Admin only. */
    @Transactional(readOnly = true)
    public FundsReport report(AppUser actor) {
        if (actor.getRole() != Role.COORDINATOR && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only Coordinator/Admin can view the funds report");
        }
        List<DisasterFundSummary> lines = disasters.findAll().stream()
                .map(d -> {
                    BigDecimal donated = totalDonated(d.getId());
                    BigDecimal procured = totalProcured(d.getId());
                    return new DisasterFundSummary(
                            d.getId(), d.getNameEn(), d.getNameBn(), donated, procured, donated.subtract(procured));
                })
                .sorted(Comparator.comparing(DisasterFundSummary::disasterId))
                .toList();

        BigDecimal totalDonated = lines.stream().map(DisasterFundSummary::donated).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalProcured = lines.stream().map(DisasterFundSummary::procured).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FundsReport(lines, totalDonated, totalProcured, totalDonated.subtract(totalProcured));
    }

    @Transactional(readOnly = true)
    public List<ProcurementView> procurementsFor(AppUser actor, Long disasterId) {
        if (actor.getRole() != Role.COORDINATOR && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only Coordinator/Admin can view procurements");
        }
        Map<Long, Camp> campById = camps.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Camp::getId, c -> c));
        return procurements.findByDisasterId(disasterId).stream()
                .map(p -> toView(p, campById.get(p.getCampId())))
                .toList();
    }

    private DonationView toView(Donation d, Disaster disaster) {
        return new DonationView(
                d.getId(), disaster.getId(), disaster.getNameEn(), disaster.getNameBn(), d.getAmount(), d.getCreatedAt());
    }

    private ProcurementView toView(Procurement p, Camp camp) {
        return new ProcurementView(
                p.getId(), p.getDisasterId(), camp.getId(), camp.getNameEn(), camp.getNameBn(),
                p.getResourceType(), p.getAmount(), p.getUnitPrice(), p.getQuantity(), p.getCreatedAt());
    }
}
