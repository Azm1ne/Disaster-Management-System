package bd.dms.api;

import bd.dms.allocation.AllocationDecision;
import bd.dms.allocation.AllocationService;
import bd.dms.allocation.AllocationStatus;
import bd.dms.allocation.dto.AllocationSummary;
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
 * The allocation decision queue surface: list what the caller is entitled to see, and drive
 * Approve/Modify/Reject as a single typed transition. Role/camp entitlement and the
 * over-allocation block are enforced in {@link AllocationService}; this controller only resolves
 * the caller and shapes responses.
 */
@RestController
@RequestMapping("/allocations")
public class AllocationController {

    public record TransitionRequest(@NotNull AllocationStatus toStatus, BigDecimal quantity, String note) {}

    private final AllocationService allocationService;
    private final UserRepository users;

    public AllocationController(AllocationService allocationService, UserRepository users) {
        this.allocationService = allocationService;
        this.users = users;
    }

    @GetMapping
    public List<AllocationSummary> list(Authentication authentication) {
        AppUser actor = actor(authentication);
        return allocationService.visibleTo(actor).stream().map(d -> toSummary(d, actor)).toList();
    }

    @PostMapping("/{id}/transition")
    public AllocationSummary transition(
            @PathVariable Long id, @Valid @RequestBody TransitionRequest request, Authentication authentication) {
        AppUser actor = actor(authentication);
        AllocationDecision decision = allocationService.transition(
                actor, id, request.toStatus(), request.quantity(), request.note());
        return toSummary(decision, actor);
    }

    private AppUser actor(Authentication authentication) {
        return users.findByUsername(authentication.getName()).orElseThrow();
    }

    private AllocationSummary toSummary(AllocationDecision decision, AppUser actor) {
        return new AllocationSummary(
                decision.getId(),
                decision.getResourceType(),
                decision.getSourceCampId(),
                decision.getTargetCampId(),
                decision.getRecommendedQuantity(),
                decision.getDecidedQuantity(),
                decision.getStatus(),
                decision.getSeverityScore(),
                decision.getShortageScore(),
                decision.getPopulationScore(),
                decision.getFairnessScore(),
                decision.getPriorityScore(),
                decision.getGeneratedAtTick(),
                decision.getDecidedAtTick(),
                allocationService.canAct(actor, decision),
                decision.getCreatedAt(),
                decision.getUpdatedAt());
    }
}
