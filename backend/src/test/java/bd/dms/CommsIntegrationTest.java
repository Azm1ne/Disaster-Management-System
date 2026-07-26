package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.dms.allocation.AllocationDecision;
import bd.dms.allocation.AllocationDecisionRepository;
import bd.dms.allocation.AllocationService;
import bd.dms.allocation.AllocationStatus;
import bd.dms.allocation.AllocationTransitionRepository;
import bd.dms.broadcast.Broadcast;
import bd.dms.broadcast.BroadcastRead;
import bd.dms.broadcast.BroadcastService;
import bd.dms.dm.DirectMessage;
import bd.dms.dm.DmRelationshipService;
import bd.dms.dm.DmService;
import bd.dms.note.Note;
import bd.dms.sim.SimulationEngine;
import bd.dms.user.AppUser;
import bd.dms.user.Role;
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
 * Ticket 12's three tiers, exercised against the real seeded demo users/camps: case notes bind
 * to the right subject (extending {@code AlertLifecycleIntegrationTest}'s note coverage to
 * allocations), broadcast read receipts are recorded per-recipient, and DM relationship
 * constraints are enforced server-side (permitted pairs succeed, everything else is refused).
 */
@SpringBootTest
class CommsIntegrationTest {

    @Autowired
    private AllocationService allocationService;

    @Autowired
    private AllocationDecisionRepository allocations;

    @Autowired
    private AllocationTransitionRepository transitions;

    @Autowired
    private BroadcastService broadcastService;

    @Autowired
    private DmService dmService;

    @Autowired
    private DmRelationshipService relationships;

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

    private AppUser user(String username) {
        return users.findByUsername(username).orElseThrow();
    }

    // ---- Tier 1: case notes bind to the right subject (allocations) ----

    @Test
    void aCaseNoteOnAnAllocationIsOnlyVisibleThroughThatAllocationsThread() {
        AllocationDecision decisionA = freshRecommendation(BigDecimal.ONE);
        AllocationDecision decisionB = freshRecommendation(BigDecimal.TEN);

        Note note = allocationService.addNote(user("coordinator"), decisionA.getId(), "Confirmed the shortage");

        assertThat(allocationService.notesFor(user("coordinator"), decisionA.getId()))
                .extracting(Note::getBody)
                .containsExactly(note.getBody());
        assertThat(allocationService.notesFor(user("coordinator"), decisionB.getId())).isEmpty();
    }

    @Test
    void aRoleWithNoVisibilityIntoAnAllocationCannotNoteOnIt() {
        AllocationDecision decision = freshRecommendation(BigDecimal.ONE);

        assertThatThrownBy(() -> allocationService.addNote(user("donor"), decision.getId(), "nope"))
                .isInstanceOf(AccessDeniedException.class);
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

    // ---- Tier 2: broadcasts + read receipts ----

    @Test
    void aBroadcastReadReceiptIsRecordedForTheReadingRecipientAndVisibleToTheSender() {
        AppUser coordinator = user("coordinator");
        AppUser campManager = user("camp_manager");

        Broadcast broadcast = broadcastService.send(
                coordinator, Role.CAMP_MANAGER, "Convoy delayed 2 hours", "কনভয় ২ ঘণ্টা বিলম্বিত");

        assertThat(broadcastService.receiptsFor(coordinator, broadcast.getId())).isEmpty();

        broadcastService.markRead(campManager, broadcast.getId());

        assertThat(broadcastService.receiptsFor(coordinator, broadcast.getId()))
                .extracting(BroadcastRead::getUserId)
                .containsExactly(campManager.getId());
    }

    @Test
    void markingReadTwiceStaysIdempotent() {
        AppUser coordinator = user("coordinator");
        AppUser campManager = user("camp_manager");
        Broadcast broadcast =
                broadcastService.send(coordinator, Role.CAMP_MANAGER, "Test", "টেস্ট");

        broadcastService.markRead(campManager, broadcast.getId());
        broadcastService.markRead(campManager, broadcast.getId());

        assertThat(broadcastService.receiptsFor(coordinator, broadcast.getId())).hasSize(1);
    }

    @Test
    void onlyTheTargetedRoleCanMarkABroadcastRead() {
        AppUser coordinator = user("coordinator");
        AppUser donor = user("donor");
        Broadcast broadcast =
                broadcastService.send(coordinator, Role.CAMP_MANAGER, "Test", "টেস্ট");

        assertThatThrownBy(() -> broadcastService.markRead(donor, broadcast.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void onlyTheSenderOrOversightCanViewReceipts() {
        AppUser coordinator = user("coordinator");
        AppUser campManager = user("camp_manager");
        Broadcast broadcast =
                broadcastService.send(coordinator, Role.CAMP_MANAGER, "Test", "টেস্ট");

        assertThatThrownBy(() -> broadcastService.receiptsFor(campManager, broadcast.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aNonOversightRoleCannotSendABroadcast() {
        AppUser campManager = user("camp_manager");
        assertThatThrownBy(() -> broadcastService.send(campManager, Role.VOLUNTEER, "x", "x"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---- Tier 3: DM relationship constraints ----

    @Test
    void coordinatorAndCampManagerMayDm() {
        assertThat(relationships.permitted(user("coordinator"), user("camp_manager"))).isTrue();
        DirectMessage sent = dmService.send(user("coordinator"), user("camp_manager").getId(), "Status?");
        assertThat(dmService.threadWith(user("camp_manager"), user("coordinator").getId()))
                .extracting(DirectMessage::getBody)
                .containsExactly(sent.getBody());
    }

    @Test
    void campManagerAndTheirVolunteerMayDm() {
        // Seeded by CampAssignmentSeeder: both camp_manager and volunteer are assigned to
        // jam-kurigram-sadar, so this is a real operational relationship out of the box.
        assertThat(relationships.permitted(user("camp_manager"), user("volunteer"))).isTrue();
        DirectMessage sent = dmService.send(user("camp_manager"), user("volunteer").getId(), "Cover gate 2");
        assertThat(dmService.threadWith(user("volunteer"), user("camp_manager").getId()))
                .extracting(DirectMessage::getBody)
                .containsExactly(sent.getBody());
    }

    @Test
    void aDonorMayNotDmAnyone() {
        assertThat(relationships.permitted(user("donor"), user("camp_manager"))).isFalse();
        assertThatThrownBy(() -> dmService.send(user("donor"), user("camp_manager").getId(), "hi"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void coordinatorMayNotDmAVolunteerDirectly() {
        assertThat(relationships.permitted(user("coordinator"), user("volunteer"))).isFalse();
        assertThatThrownBy(() -> dmService.send(user("coordinator"), user("volunteer").getId(), "hi"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void readingAThreadWithAnUnpermittedUserIsRefused() {
        assertThatThrownBy(() -> dmService.threadWith(user("donor"), user("camp_manager").getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void campManagerContactsIncludeCoordinatorAndSharedCampVolunteers() {
        var contacts = relationships.contactsFor(user("camp_manager"));
        assertThat(contacts).extracting(AppUser::getUsername).contains("coordinator", "volunteer");
    }
}
