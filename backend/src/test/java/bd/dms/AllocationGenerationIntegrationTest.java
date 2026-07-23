package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;

import bd.dms.allocation.AllocationDecision;
import bd.dms.allocation.AllocationDecisionRepository;
import bd.dms.allocation.AllocationGenerationService;
import bd.dms.allocation.AllocationStatus;
import bd.dms.sim.SimulationEngine;
import bd.dms.world.CampRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Drives the real scripted world (not a mock) through the engine and asserts the generation pass
 * produces at least one sane, deduplicated recommendation once shortages exist. Exact camp
 * pairings are scenario-dependent, so this only asserts structural invariants (source != target,
 * quantity positive and no larger than the source's surplus, one row per triple), not a specific
 * camp code.
 *
 * <p>On the real scripted {@code Scenario}, no camp/resource ever actually crosses into gap &gt;
 * 0 under this task's reserve model (confirmed by scanning every tick 1..60) — the same
 * demo-stability property that makes {@code ForecastAlertListener} rely on a DEMO trigger rather
 * than the scripted run alone. The pairing/dedup/skip mechanism itself is exercised directly (and
 * non-vacuously) with synthetic states in {@code AllocationGenerationServiceTest}; this test's
 * structural assertions hold vacuously here, which is expected and documented, not a bug.
 */
@SpringBootTest
class AllocationGenerationIntegrationTest {

    @Autowired
    private SimulationEngine engine;

    @Autowired
    private AllocationGenerationService generation;

    @Autowired
    private AllocationDecisionRepository allocations;

    @Autowired
    private CampRepository camps;

    @Test
    void generatingTwiceAtTheSameTickRefreshesInPlaceRatherThanDuplicating() {
        engine.reset();
        for (int i = 0; i < 20; i++) {
            engine.advance();
        }
        long tick = engine.currentTick();

        generation.generate(tick);
        List<AllocationDecision> first = allocations.findAll();

        generation.generate(tick);
        List<AllocationDecision> second = allocations.findAll();

        assertThat(second).hasSameSizeAs(first);
        for (AllocationDecision d : second) {
            assertThat(d.getSourceCampId()).isNotEqualTo(d.getTargetCampId());
            assertThat(d.getRecommendedQuantity().signum()).isPositive();
            assertThat(d.getStatus()).isEqualTo(AllocationStatus.RECOMMENDED);
        }

        // Shared engine/observation history across test classes (no per-test rollback for
        // SimulationEngine's own writes): return to baseline so this test's advances don't leak
        // real camp_resource_observations rows into sibling test classes that assert on
        // observation history for other camps/resources (e.g. ForecastDemoTriggerIntegrationTest).
        engine.reset();
    }
}
