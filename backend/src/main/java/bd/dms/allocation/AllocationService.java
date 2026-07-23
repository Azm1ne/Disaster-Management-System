package bd.dms.allocation;

import bd.dms.sim.SimulationEngine;
import bd.dms.user.AppUser;
import bd.dms.user.Role;
import bd.dms.world.CampAssignmentRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sole writer of allocation state (mirrors {@code AlertService} and, before it,
 * {@code SimulationEngine} for camp state). Never touches {@code camp_resources} — approving an
 * allocation only changes this row's own status; physical fulfillment is out of scope for this
 * ticket. The hard over-allocation block is re-checked here, fresh, at decision time (not at
 * recommendation-generation time), against the source camp's *current* surplus minus every other
 * still-live (non-{@code REJECTED}) allocation already drawing on that same source+resource — the
 * world may have ticked forward since the recommendation was generated.
 */
@Service
public class AllocationService {

    private static final List<AllocationStatus> NON_REJECTED = List.of(
            AllocationStatus.RECOMMENDED, AllocationStatus.APPROVED, AllocationStatus.MODIFIED);

    /** Camp managers only see allocations actually approved for their camp (spec item 49 /
     * {@code allocations.subtitleCampManager}) — not still-pending {@code RECOMMENDED} rows, and
     * not {@code REJECTED} ones. */
    private static final List<AllocationStatus> APPROVED_FOR_CAMP_MANAGER = List.of(
            AllocationStatus.APPROVED, AllocationStatus.MODIFIED);

    private final AllocationDecisionRepository allocations;
    private final AllocationTransitionRepository transitions;
    private final AllocationScoringService scoring;
    private final CampAssignmentRepository assignments;
    private final SimulationEngine engine;

    public AllocationService(
            AllocationDecisionRepository allocations,
            AllocationTransitionRepository transitions,
            AllocationScoringService scoring,
            CampAssignmentRepository assignments,
            SimulationEngine engine) {
        this.allocations = allocations;
        this.transitions = transitions;
        this.scoring = scoring;
        this.assignments = assignments;
        this.engine = engine;
    }

    @Transactional
    public AllocationDecision transition(
            AppUser actor, Long allocationId, AllocationStatus toStatus, BigDecimal quantity, String note) {
        AllocationDecision decision = allocations.findById(allocationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown allocation: " + allocationId));
        if (!canAct(actor, decision)) {
            throw new AccessDeniedException("Not entitled to act on this allocation");
        }
        if (!AllocationTransitionRules.isLegal(decision.getStatus(), toStatus)) {
            throw new IllegalArgumentException(
                    "Illegal transition: " + decision.getStatus() + " -> " + toStatus);
        }

        BigDecimal decidedQuantity = resolveDecidedQuantity(decision, toStatus, quantity);
        long tick = engine.currentTick();
        if (toStatus == AllocationStatus.APPROVED || toStatus == AllocationStatus.MODIFIED) {
            BigDecimal available = availableSurplus(decision, tick);
            if (decidedQuantity.compareTo(available) > 0) {
                throw new IllegalArgumentException(
                        "Requested quantity " + decidedQuantity + " exceeds available surplus " + available
                                + " at source camp " + decision.getSourceCampId());
            }
        }

        AllocationStatus from = decision.getStatus();
        decision.applyDecision(toStatus, decidedQuantity, tick);
        transitions.save(new AllocationTransition(decision.getId(), from, toStatus, actor.getId(), note, tick));
        return decision;
    }

    private BigDecimal resolveDecidedQuantity(AllocationDecision decision, AllocationStatus toStatus, BigDecimal quantity) {
        return switch (toStatus) {
            case APPROVED -> decision.getRecommendedQuantity();
            case MODIFIED -> {
                if (quantity == null || quantity.signum() <= 0) {
                    throw new IllegalArgumentException("Modified quantity must be positive");
                }
                yield quantity;
            }
            case REJECTED -> null;
            case RECOMMENDED -> throw new IllegalArgumentException("Cannot transition back to RECOMMENDED");
        };
    }

    private BigDecimal availableSurplus(AllocationDecision decision, long tick) {
        BigDecimal surplus = scoring
                .resourceState(decision.getSourceCampId(), decision.getResourceType(), tick)
                .surplus();
        BigDecimal committed = allocations
                .findBySourceCampIdAndResourceTypeAndStatusIn(
                        decision.getSourceCampId(), decision.getResourceType(), NON_REJECTED)
                .stream()
                .filter(other -> !other.getId().equals(decision.getId()))
                .map(AllocationDecision::effectiveQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return surplus.subtract(committed).max(BigDecimal.ZERO);
    }

    public List<AllocationDecision> visibleTo(AppUser actor) {
        if (isOversight(actor)) {
            return allocations.findAll();
        }
        if (actor.getRole() != Role.CAMP_MANAGER) {
            return List.of();
        }
        List<Long> campIds = assignments.findByUserId(actor.getId()).stream()
                .map(bd.dms.world.CampAssignment::getCampId)
                .toList();
        return allocations.findByTargetCampIdInAndStatusIn(campIds, APPROVED_FOR_CAMP_MANAGER);
    }

    /** Camp managers are read-only for allocations (see spec item 49) — only Coordinator/Admin
     * may Approve/Modify/Reject. */
    public boolean canAct(AppUser actor, AllocationDecision decision) {
        return isOversight(actor);
    }

    public List<AllocationTransition> transitionsFor(Long allocationId) {
        return transitions.findByAllocationIdOrderByAtTickAsc(allocationId);
    }

    private boolean isOversight(AppUser actor) {
        return actor.getRole() == Role.COORDINATOR || actor.getRole() == Role.ADMIN;
    }
}
