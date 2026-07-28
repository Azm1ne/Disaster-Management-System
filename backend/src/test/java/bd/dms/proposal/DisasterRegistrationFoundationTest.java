package bd.dms.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import bd.dms.user.AppUser;
import bd.dms.user.Role;
import bd.dms.user.UserRepository;
import bd.dms.world.Disaster;
import bd.dms.world.DisasterRepository;
import bd.dms.world.GeometryHistory;
import bd.dms.world.GeometryHistoryRepository;
import bd.dms.world.GeometrySubjectType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Ticket 13 task 1 foundation: proves the app boots with V16 applied, the two new
 * repositories round-trip real rows against FK-constrained columns (actor/proposer/target
 * disaster ids all reference real seeded rows, not arbitrary ids), Role.CENTRAL_AUTHORITY
 * round-trips through UserRepository, and DemoUserSeeder creates the central_authority
 * demo user on boot — matching the fixture style of FamilyMemberRepositoryTest.
 */
@SpringBootTest
class DisasterRegistrationFoundationTest {

    @Autowired
    private GeometryHistoryRepository geometryHistory;

    @Autowired
    private DisasterProposalRepository proposals;

    @Autowired
    private UserRepository users;

    @Autowired
    private DisasterRepository disasters;

    @Test
    void demoUserSeederCreatesCentralAuthorityUser() {
        Optional<AppUser> centralAuthority = users.findByUsername("central_authority");

        assertThat(centralAuthority).isPresent();
        assertThat(centralAuthority.get().getRole()).isEqualTo(Role.CENTRAL_AUTHORITY);
    }

    @Test
    void roleCentralAuthorityRoundTripsThroughUserRepository() {
        AppUser saved = users.save(
                new AppUser("test-central-authority-2", "hash", Role.CENTRAL_AUTHORITY, "Test CA", "Test CA"));

        Optional<AppUser> reloaded = users.findById(saved.getId());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getRole()).isEqualTo(Role.CENTRAL_AUTHORITY);
        assertThat(users.findByRole(Role.CENTRAL_AUTHORITY)).extracting(AppUser::getId).contains(saved.getId());
    }

    @Test
    void geometryHistorySavesAndFindsRowsOrderedByCreatedAt() {
        Disaster disaster = disasters.findAll().get(0);
        AppUser actor = users.findByUsername("admin").orElseThrow();

        GeometryHistory first = geometryHistory.save(new GeometryHistory(
                GeometrySubjectType.DISASTER, disaster.getId(), null, "{\"type\":\"Polygon\",\"v\":1}",
                actor.getId()));
        GeometryHistory second = geometryHistory.save(new GeometryHistory(
                GeometrySubjectType.DISASTER, disaster.getId(), first.getNewGeometry(),
                "{\"type\":\"Polygon\",\"v\":2}", actor.getId()));

        List<GeometryHistory> history = geometryHistory.findBySubjectTypeAndSubjectIdOrderByCreatedAtAsc(
                GeometrySubjectType.DISASTER, disaster.getId());

        assertThat(history).extracting(GeometryHistory::getId).containsExactly(first.getId(), second.getId());
        assertThat(history.get(0).getPreviousGeometry()).isNull();
        assertThat(history.get(1).getPreviousGeometry()).isEqualTo("{\"type\":\"Polygon\",\"v\":1}");
    }

    @Test
    void disasterProposalSavesFindsByStatusAndRoundTripsApproval() {
        Disaster disaster = disasters.findAll().get(0);
        AppUser coordinator = users.findByUsername("coordinator").orElseThrow();
        AppUser centralAuthority = users.findByUsername("central_authority").orElseThrow();

        int baselinePending = proposals.findByStatus(ProposalStatus.PENDING).size();

        DisasterProposal pending = proposals.save(new DisasterProposal(
                ProposalType.DISASTER_UPDATE, disaster.getId(), "{\"nameEn\":\"Updated name\"}",
                coordinator.getId()));
        DisasterProposal toApprove = proposals.save(new DisasterProposal(
                ProposalType.AFFECTED_AREA_CREATE, disaster.getId(), "{\"nameEn\":\"New area\"}",
                coordinator.getId()));

        assertThat(proposals.findByStatus(ProposalStatus.PENDING)).hasSize(baselinePending + 2);

        toApprove.approve(centralAuthority.getId(), "looks fine");
        proposals.save(toApprove);

        DisasterProposal reloaded = proposals.findById(toApprove.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ProposalStatus.APPROVED);
        assertThat(reloaded.getReviewedByUserId()).isEqualTo(centralAuthority.getId());
        assertThat(reloaded.getReviewNote()).isEqualTo("looks fine");
        assertThat(reloaded.getReviewedAt()).isNotNull();
        assertThat(proposals.findByStatus(ProposalStatus.PENDING)).hasSize(baselinePending + 1);

        assertThat(pending.getTargetDisasterId()).isEqualTo(disaster.getId());
    }
}
