package bd.dms.api;

import bd.dms.allocation.AllocationGenerationService;
import bd.dms.anomaly.AllocationBurstDetector;
import bd.dms.anomaly.AnomalyFlag;
import bd.dms.anomaly.AnomalyFlagStatus;
import bd.dms.anomaly.AnomalyReviewService;
import bd.dms.anomaly.dto.AnomalyFlagView;
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
 * The anomaly review surface: list flags an oversight actor is entitled to see and drive a
 * review decision, plus (DEMO-only) a trigger that produces a real {@code AllocationBurstDetector}
 * flag on demand — same DEMO-trigger precedent as {@code AllocationController.demo} and {@code
 * ForecastController.demo}. The scripted {@code Scenario} never naturally produces three
 * allocation recommendations at the same tick, so {@code POST /anomalies/demo/burst/...} seeds
 * transient synthetic {@code CampResourceObservation} rows for all three resource types across two
 * real camps, runs the real generation + burst-scan pipeline, then deletes only the synthetic
 * observations.
 */
@RestController
@RequestMapping("/anomalies")
public class AnomalyController {

    public record ReviewRequest(@NotNull AnomalyFlagStatus toStatus, String note) {}

    private static final List<String> RESOURCE_TYPES = List.of("WATER", "FOOD", "MEDICAL");

    private final AnomalyReviewService reviewService;
    private final UserRepository users;
    private final CampResourceObservationRepository observations;
    private final AllocationGenerationService generation;
    private final AllocationBurstDetector burstDetector;
    private final SimulationEngine engine;

    public AnomalyController(
            AnomalyReviewService reviewService,
            UserRepository users,
            CampResourceObservationRepository observations,
            AllocationGenerationService generation,
            AllocationBurstDetector burstDetector,
            SimulationEngine engine) {
        this.reviewService = reviewService;
        this.users = users;
        this.observations = observations;
        this.generation = generation;
        this.burstDetector = burstDetector;
        this.engine = engine;
    }

    @GetMapping
    public List<AnomalyFlagView> list(Authentication authentication) {
        AppUser actor = actor(authentication);
        return reviewService.list(actor).stream().map(this::toView).toList();
    }

    @PostMapping("/{id}/review")
    public AnomalyFlagView review(
            @PathVariable Long id, @Valid @RequestBody ReviewRequest request, Authentication authentication) {
        AppUser actor = actor(authentication);
        AnomalyFlag flag = reviewService.review(actor, id, request.toStatus(), request.note());
        return toView(flag);
    }

    @PostMapping("/demo/burst/{shortageCampId}/{surplusCampId}")
    @Transactional
    public ResponseEntity<Void> demoBurst(
            @PathVariable Long shortageCampId, @PathVariable Long surplusCampId, Authentication authentication) {
        AppUser actor = actor(authentication);
        if (actor.getRole() != Role.COORDINATOR && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only Coordinator/Admin can trigger a demo anomaly burst");
        }

        // Anchor the 5-point windows the same way AllocationController.demo does, so observedTick
        // never goes negative on a freshly reset/early demo run.
        long tick = Math.max(engine.currentTick(), 4L);
        List<Long> seededTicks = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            seededTicks.add(tick - 4 + i);
        }

        for (String resourceType : RESOURCE_TYPES) {
            for (int i = 0; i <= 4; i++) {
                long observedTick = seededTicks.get(i);
                // Shortage camp: a steep depletion, so its forecast rate is well above zero and
                // its current quantity falls short of the 10-tick reserve — a real gap.
                BigDecimal shortageQuantity = BigDecimal.valueOf(600).multiply(BigDecimal.valueOf(1.0 - i * 0.2));
                observations.save(new CampResourceObservation(shortageCampId, resourceType, shortageQuantity, observedTick));
                // Surplus camp: a flat high quantity, so its forecast rate is zero, its reserve
                // requirement is zero, and its whole seeded quantity counts as surplus.
                observations.save(new CampResourceObservation(surplusCampId, resourceType, BigDecimal.valueOf(5000), observedTick));
            }
        }

        try {
            generation.generate(tick);
            burstDetector.scan(tick);
        } finally {
            // The recommendations/flag (if any) are already persisted independently; the
            // synthetic observation rows must not outlive this request.
            for (String resourceType : RESOURCE_TYPES) {
                observations.deleteByCampIdAndResourceTypeAndTickIn(shortageCampId, resourceType, seededTicks);
                observations.deleteByCampIdAndResourceTypeAndTickIn(surplusCampId, resourceType, seededTicks);
            }
        }
        return ResponseEntity.ok().build();
    }

    private AppUser actor(Authentication authentication) {
        return users.findByUsername(authentication.getName()).orElseThrow();
    }

    private AnomalyFlagView toView(AnomalyFlag flag) {
        return new AnomalyFlagView(
                flag.getId(),
                flag.getDetectorType(),
                flag.getScore(),
                flag.getSummary(),
                flag.getInnocentExplanation(),
                flag.getSubjectIds(),
                flag.getDetectedAtTick(),
                flag.getStatus(),
                flag.getReviewedByUserId(),
                flag.getReviewNote(),
                flag.getReviewedAt(),
                flag.getCreatedAt());
    }
}
