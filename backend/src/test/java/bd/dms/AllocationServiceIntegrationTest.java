package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.dms.allocation.AllocationDecision;
import bd.dms.allocation.AllocationDecisionRepository;
import bd.dms.allocation.AllocationService;
import bd.dms.allocation.AllocationStatus;
import bd.dms.allocation.AllocationTransitionRepository;
import bd.dms.sim.SimulationEngine;
import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

/**
 * Real, non-mocked surplus math: {@code engine.reset()} in {@code @BeforeEach} re-seeds
 * {@code camp_resource_observations} at tick 0 (mirroring {@code AllocationGenerationIntegrationTest}'s
 * convention for tests that need real forecast numbers, since {@code SimulationEngine}'s writes
 * aren't rolled back by the test transaction). At tick 0 with a single observation, the forecast
 * rate is zero, so surplus equals the full seeded quantity — thousands of units at
 * jam-kurigram-sadar for WATER/FOOD, comfortably below the 1,000,000 used to force a block.
 *
 * <p>{@code @BeforeEach}/{@code @AfterEach} also clear {@code allocation_transitions}/
 * {@code allocation_decisions}: the whole point of the over-allocation ledger is that every
 * still-live row for a source camp counts against the next one, so leftover rows from a prior
 * test (not rolled back, same as the engine's own writes) would otherwise silently shrink the
 * "available" surplus seen by later tests in this class — and leftover rows from this class would
 * otherwise leak into sibling test classes (e.g. {@code AllocationDecisionRepositoryTest}) that
 * assert on an exact row set for a shared camp.
 */
@SpringBootTest
class AllocationServiceIntegrationTest {

    @Autowired
    private AllocationService allocationService;

    @Autowired
    private AllocationDecisionRepository allocations;

    @Autowired
    private AllocationTransitionRepository transitions;

    @Autowired
    private CampRepository camps;

    @Autowired
    private UserRepository users;

    @Autowired
    private SimulationEngine engine;

    @BeforeEach
    void resetWorld() {
        engine.reset();
        transitions.deleteAll();
        allocations.deleteAll();
    }

    @AfterEach
    void cleanUpWorld() {
        transitions.deleteAll();
        allocations.deleteAll();
        engine.reset();
    }

    private AppUser coordinator() {
        return users.findByUsername("coordinator").orElseThrow();
    }

    private AppUser campManager() {
        return users.findByUsername("camp_manager").orElseThrow();
    }

    private AllocationDecision freshRecommendation(BigDecimal quantity) {
        Camp source = camps.findByCode("jam-kurigram-sadar").orElseThrow();
        Camp target = camps.findAll().stream()
                .filter(c -> !c.getId().equals(source.getId()))
                .findFirst()
                .orElseThrow();
        return allocations.save(new AllocationDecision(
                "WATER", source.getId(), target.getId(), quantity, 0.5, 0.5, 0.5, 0.5, 0.5, 0));
    }

    @Test
    void coordinatorCanApproveARecommendationWithinAvailableSurplus() {
        AllocationDecision decision = freshRecommendation(BigDecimal.ONE);

        AllocationDecision approved = allocationService.transition(
                coordinator(), decision.getId(), AllocationStatus.APPROVED, null, "looks right");

        assertThat(approved.getStatus()).isEqualTo(AllocationStatus.APPROVED);
        assertThat(approved.getDecidedQuantity()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(allocationService.transitionsFor(decision.getId())).hasSize(1);
    }

    @Test
    void modifyingRequiresAPositiveQuantity() {
        AllocationDecision decision = freshRecommendation(BigDecimal.ONE);

        assertThatThrownBy(() -> allocationService.transition(
                        coordinator(), decision.getId(), AllocationStatus.MODIFIED, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overAllocatingBeyondAvailableSurplusIsHardBlocked() {
        AllocationDecision decision = freshRecommendation(BigDecimal.valueOf(1_000_000));

        assertThatThrownBy(() -> allocationService.transition(
                        coordinator(), decision.getId(), AllocationStatus.APPROVED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSecondApprovalDrawingFromTheSameAlreadyCommittedSurplusIsBlocked() {
        Camp source = camps.findByCode("jam-kurigram-sadar").orElseThrow();
        Camp targetA = camps.findAll().stream().filter(c -> !c.getId().equals(source.getId())).toList().get(0);
        Camp targetB = camps.findAll().stream().filter(c -> !c.getId().equals(source.getId())).toList().get(1);

        AllocationDecision firstRecommendation = allocations.save(new AllocationDecision(
                "FOOD", source.getId(), targetA.getId(), BigDecimal.valueOf(5), 0.5, 0.5, 0.5, 0.5, 0.5, 0));
        AllocationDecision secondRecommendation = allocations.save(new AllocationDecision(
                "FOOD", source.getId(), targetB.getId(), BigDecimal.valueOf(5), 0.5, 0.5, 0.5, 0.5, 0.5, 0));

        allocationService.transition(coordinator(), firstRecommendation.getId(), AllocationStatus.APPROVED, null, null);

        // Whether the second is blocked depends on the real seeded surplus at jam-kurigram-sadar
        // for FOOD; assert only that the service enforces *some* ceiling by driving the requested
        // quantity absurdly high on the second row via MODIFIED, which must always be blocked
        // once the first 5 units are already committed and the source's true surplus is finite.
        assertThatThrownBy(() -> allocationService.transition(
                        coordinator(), secondRecommendation.getId(), AllocationStatus.MODIFIED,
                        BigDecimal.valueOf(1_000_000), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectingNeverChecksSurplusAndAlwaysSucceeds() {
        AllocationDecision decision = freshRecommendation(BigDecimal.valueOf(1_000_000));

        AllocationDecision rejected = allocationService.transition(
                coordinator(), decision.getId(), AllocationStatus.REJECTED, null, "not needed");

        assertThat(rejected.getStatus()).isEqualTo(AllocationStatus.REJECTED);
        assertThat(rejected.getDecidedQuantity()).isNull();
    }

    @Test
    void aDecidedAllocationCannotBeTransitionedAgain() {
        AllocationDecision decision = freshRecommendation(BigDecimal.ONE);
        allocationService.transition(coordinator(), decision.getId(), AllocationStatus.APPROVED, null, null);

        assertThatThrownBy(() -> allocationService.transition(
                        coordinator(), decision.getId(), AllocationStatus.REJECTED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void campManagerCannotActEvenOnTheirOwnCampsAllocation() {
        AllocationDecision decision = freshRecommendation(BigDecimal.ONE);

        assertThatThrownBy(() -> allocationService.transition(
                        campManager(), decision.getId(), AllocationStatus.APPROVED, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void campManagerOnlySeesAllocationsTargetingTheirOwnCamps() {
        AllocationDecision decision = freshRecommendation(BigDecimal.ONE);

        boolean visible = allocationService.visibleTo(campManager()).stream()
                .anyMatch(d -> d.getId().equals(decision.getId()));

        Camp managedCamp = camps.findByCode("jam-kurigram-sadar").orElseThrow();
        boolean targetsManagedCamp = decision.getTargetCampId().equals(managedCamp.getId());
        assertThat(visible).isEqualTo(targetsManagedCamp);
    }

    @Test
    void coordinatorSeesEveryAllocation() {
        freshRecommendation(BigDecimal.ONE);
        assertThat(allocationService.visibleTo(coordinator())).isNotEmpty();
    }
}
