package bd.dms.api;

import bd.dms.allocation.AllocationDecision;
import bd.dms.allocation.AllocationGenerationService;
import bd.dms.allocation.AllocationService;
import bd.dms.allocation.AllocationStatus;
import bd.dms.allocation.dto.AllocationSummary;
import bd.dms.forecast.CampResourceObservation;
import bd.dms.forecast.CampResourceObservationRepository;
import bd.dms.sim.SimulationEngine;
import bd.dms.user.AppUser;
import bd.dms.user.Role;
import bd.dms.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The allocation decision queue surface: list what the caller is entitled to see, drive
 * Approve/Modify/Reject as a single typed transition, and (DEMO-only) trigger a real recommendation
 * on demand. Role/camp entitlement and the over-allocation block are enforced in
 * {@link AllocationService}; this controller only resolves the caller and shapes responses.
 *
 * <p>The scripted {@code Scenario} never naturally produces a shortage/surplus pair (see
 * {@code AllocationGenerationIntegrationTest}'s class doc), so {@code POST /allocations/demo/...}
 * exists to demonstrate the real pipeline live — the same DEMO-trigger precedent as
 * {@code ForecastController.demo}. It seeds transient synthetic {@code CampResourceObservation}
 * rows for two real camps (one steeply depleted for the shortage, one flat-and-high for the
 * surplus), runs the real {@link AllocationGenerationService#generate(long)} sweep, then deletes
 * only the synthetic observations — the resulting {@code AllocationDecision} row is left in place
 * for the coordinator to act on.
 */
@RestController
@RequestMapping("/allocations")
public class AllocationController {

    public record TransitionRequest(@NotNull AllocationStatus toStatus, BigDecimal quantity, String note) {}

    private static final List<String> RESOURCE_TYPES = List.of("WATER", "FOOD", "MEDICAL");

    private final AllocationService allocationService;
    private final UserRepository users;
    private final AllocationGenerationService generation;
    private final CampResourceObservationRepository observations;
    private final SimulationEngine engine;

    public AllocationController(
            AllocationService allocationService,
            UserRepository users,
            AllocationGenerationService generation,
            CampResourceObservationRepository observations,
            SimulationEngine engine) {
        this.allocationService = allocationService;
        this.users = users;
        this.generation = generation;
        this.observations = observations;
        this.engine = engine;
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

    @PostMapping("/demo/{shortageCampId}/{surplusCampId}/{resourceType}")
    @Transactional
    public ResponseEntity<Void> demo(
            @PathVariable Long shortageCampId,
            @PathVariable Long surplusCampId,
            @PathVariable String resourceType,
            Authentication authentication) {
        AppUser actor = actor(authentication);
        if (actor.getRole() != Role.COORDINATOR && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only Coordinator/Admin can trigger a demo allocation");
        }
        if (!RESOURCE_TYPES.contains(resourceType)) {
            throw new IllegalArgumentException("Unknown resource type: " + resourceType);
        }

        // Anchor the 5-point windows the same way ForecastController.demo does, so observedTick
        // never goes negative on a freshly reset/early demo run.
        long baseTick = Math.max(engine.currentTick(), 4L);
        List<Long> seededTicks = new ArrayList<>();

        for (int i = 0; i <= 4; i++) {
            long observedTick = baseTick - 4 + i;
            seededTicks.add(observedTick);
            // Shortage camp: a steep depletion, so its forecast rate is well above zero and its
            // current quantity falls short of the 10-tick reserve — a real gap.
            BigDecimal shortageQuantity = BigDecimal.valueOf(600).multiply(BigDecimal.valueOf(1.0 - i * 0.2));
            observations.save(new CampResourceObservation(shortageCampId, resourceType, shortageQuantity, observedTick));
            // Surplus camp: a flat high quantity, so its forecast rate is zero, its reserve
            // requirement is zero, and its whole seeded quantity counts as surplus.
            observations.save(new CampResourceObservation(surplusCampId, resourceType, BigDecimal.valueOf(5000), observedTick));
        }

        try {
            generation.generate(baseTick);
        } finally {
            // The recommendation (if any) is already persisted independently in
            // allocation_decisions; the synthetic observation rows must not outlive this request.
            observations.deleteByCampIdAndResourceTypeAndTickIn(shortageCampId, resourceType, seededTicks);
            observations.deleteByCampIdAndResourceTypeAndTickIn(surplusCampId, resourceType, seededTicks);
        }
        return ResponseEntity.ok().build();
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
