package bd.dms.allocation;

import static org.assertj.core.api.Assertions.assertThat;

import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * allocation_decisions.source_camp_id/.target_camp_id are FK-constrained (V11__allocation_decisions.sql),
 * so test fixtures need real seeded camps rather than arbitrary ids — mirrors the same convention
 * used by FamilyMemberRepositoryTest.
 */
@SpringBootTest
class AllocationDecisionRepositoryTest {

    @Autowired
    private AllocationDecisionRepository allocations;

    @Autowired
    private CampRepository camps;

    private AllocationDecision decision(Long source, Long target, AllocationStatus status, long decidedAtTick) {
        AllocationDecision d = new AllocationDecision(
                "WATER", source, target, BigDecimal.TEN, 0.5, 0.5, 0.5, 0.5, 0.5, 10);
        if (status != AllocationStatus.RECOMMENDED) {
            d.applyDecision(status, BigDecimal.TEN, decidedAtTick);
        }
        return d;
    }

    @Test
    void findsByTargetCampIdsAndBySourceStatusAndByTargetStatus() {
        List<Camp> allCamps = camps.findAllByOrderByNameEnAsc();
        long campA = allCamps.get(0).getId();
        long campB = allCamps.get(1).getId();
        long campC = allCamps.get(2).getId();
        long campD = allCamps.get(3).getId();
        long campE = allCamps.get(4).getId();

        AllocationDecision recommended = allocations.save(decision(campA, campB, AllocationStatus.RECOMMENDED, 0));
        AllocationDecision approved = allocations.save(decision(campA, campC, AllocationStatus.APPROVED, 15));
        allocations.save(decision(campD, campE, AllocationStatus.RECOMMENDED, 0));

        assertThat(allocations.findByTargetCampIdIn(List.of(campB, campC)))
                .extracting(AllocationDecision::getId)
                .containsExactlyInAnyOrder(recommended.getId(), approved.getId());

        assertThat(allocations
                .findBySourceCampIdAndTargetCampIdAndResourceTypeAndStatus(campA, campB, "WATER", AllocationStatus.RECOMMENDED))
                .isPresent();

        assertThat(allocations.findBySourceCampIdAndResourceTypeAndStatusIn(
                        campA, "WATER", List.of(AllocationStatus.RECOMMENDED, AllocationStatus.APPROVED)))
                .hasSize(2);

        assertThat(allocations.findByTargetCampIdAndResourceTypeAndStatusIn(
                        campC, "WATER", List.of(AllocationStatus.APPROVED, AllocationStatus.MODIFIED)))
                .extracting(AllocationDecision::getId)
                .containsExactly(approved.getId());
    }
}
