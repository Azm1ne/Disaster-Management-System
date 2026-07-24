package bd.dms.allocation;

import bd.dms.sim.WorldChangedEvent;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On every simulated tick, pairs each camp with a resource shortage (gap &gt; 0) against the
 * single other camp with the largest surplus of that same resource, and records/refreshes one
 * {@code RECOMMENDED} {@link AllocationDecision} per (source, target, resourceType) triple. Never
 * writes {@code camp_resources} — see {@code AllocationService}'s class doc. If no camp anywhere
 * has surplus of a resource, every shortage for that resource is skipped this pass (no
 * procurement source exists yet — a documented limitation, not a bug).
 */
@Component
public class AllocationGenerationService {

    private static final List<String> RESOURCE_TYPES = List.of("WATER", "FOOD", "MEDICAL");

    private final CampRepository camps;
    private final AllocationScoringService scoring;
    private final AllocationDecisionRepository allocations;

    public AllocationGenerationService(
            CampRepository camps, AllocationScoringService scoring, AllocationDecisionRepository allocations) {
        this.camps = camps;
        this.scoring = scoring;
        this.allocations = allocations;
    }

    @EventListener
    public void onWorldChanged(WorldChangedEvent event) {
        if (!event.worldChanged()) {
            return;
        }
        generate(event.clock().tick());
    }

    public void generate(long tick) {
        List<Camp> allCamps = camps.findAll();
        for (String resourceType : RESOURCE_TYPES) {
            generateForResource(allCamps, resourceType, tick);
        }
    }

    private void generateForResource(List<Camp> allCamps, String resourceType, long tick) {
        Map<Long, AllocationScoringService.CampResourceState> statesByCampId = new HashMap<>();
        for (Camp camp : allCamps) {
            statesByCampId.put(camp.getId(), scoring.resourceState(camp.getId(), resourceType, tick));
        }

        List<Camp> shortageCamps = new ArrayList<>();
        for (Camp camp : allCamps) {
            if (statesByCampId.get(camp.getId()).gap().signum() > 0) {
                shortageCamps.add(camp);
            }
        }
        if (shortageCamps.isEmpty()) {
            return;
        }

        int maxPopulation = shortageCamps.stream().mapToInt(Camp::getPopulation).max().orElse(0);

        Map<Long, Long> rawFairnessByCampId = new HashMap<>();
        for (Camp camp : shortageCamps) {
            rawFairnessByCampId.put(camp.getId(), scoring.ticksSinceLastApproved(camp.getId(), resourceType, tick));
        }
        long maxFairnessRaw = rawFairnessByCampId.values().stream()
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);

        for (Camp shortageCamp : shortageCamps) {
            Camp bestSource = bestSurplusSource(allCamps, statesByCampId, shortageCamp.getId());
            if (bestSource == null) {
                continue;
            }
            AllocationScoringService.CampResourceState shortageState = statesByCampId.get(shortageCamp.getId());
            AllocationScoringService.CampResourceState sourceState = statesByCampId.get(bestSource.getId());
            BigDecimal quantity = shortageState.gap().min(sourceState.surplus());
            if (quantity.signum() <= 0) {
                continue;
            }

            double severity = scoring.severityScore(shortageCamp.getId());
            double shortage = scoring.shortageScore(shortageState.ticksRemainingWorstCase());
            double population = maxPopulation <= 0
                    ? 1.0
                    : AllocationScoringService.clamp01((double) shortageCamp.getPopulation() / maxPopulation);
            Long rawFairness = rawFairnessByCampId.get(shortageCamp.getId());
            double fairness = maxFairnessRaw <= 0
                    ? 1.0
                    : AllocationScoringService.clamp01(
                            (double) (rawFairness == null ? maxFairnessRaw : rawFairness) / maxFairnessRaw);
            double priority = scoring.priorityScore(severity, shortage, population, fairness);

            upsert(bestSource.getId(), shortageCamp.getId(), resourceType, quantity,
                    severity, shortage, population, fairness, priority, tick);
        }
    }

    private Camp bestSurplusSource(
            List<Camp> allCamps,
            Map<Long, AllocationScoringService.CampResourceState> statesByCampId,
            Long excludeCampId) {
        Camp best = null;
        BigDecimal bestSurplus = BigDecimal.ZERO;
        for (Camp camp : allCamps) {
            if (camp.getId().equals(excludeCampId)) {
                continue;
            }
            BigDecimal surplus = statesByCampId.get(camp.getId()).surplus();
            if (surplus.compareTo(bestSurplus) > 0) {
                best = camp;
                bestSurplus = surplus;
            }
        }
        return best;
    }

    private void upsert(
            Long sourceCampId,
            Long targetCampId,
            String resourceType,
            BigDecimal quantity,
            double severity,
            double shortage,
            double population,
            double fairness,
            double priority,
            long tick) {
        allocations
                .findBySourceCampIdAndTargetCampIdAndResourceTypeAndStatus(
                        sourceCampId, targetCampId, resourceType, AllocationStatus.RECOMMENDED)
                .ifPresentOrElse(
                        existing -> existing.refreshRecommendation(
                                quantity, severity, shortage, population, fairness, priority, tick),
                        () -> allocations.save(new AllocationDecision(
                                resourceType, sourceCampId, targetCampId, quantity,
                                severity, shortage, population, fairness, priority, tick)));
    }
}
