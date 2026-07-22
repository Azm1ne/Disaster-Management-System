package bd.dms.family;

import bd.dms.family.dto.ArrivalStatus;
import bd.dms.family.dto.CampArrivalGroup;
import bd.dms.family.dto.CampArrivalsView;
import bd.dms.family.dto.FamilyGroupStatus;
import bd.dms.family.dto.MemberInput;
import bd.dms.family.dto.MemberView;
import bd.dms.family.dto.RegisterGroupRequest;
import bd.dms.family.dto.ReunificationResult;
import bd.dms.user.Role;
import bd.dms.world.Camp;
import bd.dms.world.CampAssignmentRepository;
import bd.dms.world.CampRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, arrival, and reunification for {@link FamilyGroup}s. This is the first
 * user-driven writer in the world model — deliberately kept off {@code camps}/{@code
 * camp_resources}, which stay the simulation engine's alone (see {@code SimulationEngine}), so
 * the two writers cannot collide.
 */
@Service
@Transactional
public class FamilyService {

    private final FamilyGroupRepository groups;
    private final FamilyMemberRepository members;
    private final CampRepository camps;
    private final CampAssignmentRepository assignments;

    public FamilyService(
            FamilyGroupRepository groups,
            FamilyMemberRepository members,
            CampRepository camps,
            CampAssignmentRepository assignments) {
        this.groups = groups;
        this.members = members;
        this.camps = camps;
        this.assignments = assignments;
    }

    /** Registers a household as a single group. A solo victim submits a one-element member list. */
    public FamilyGroupStatus registerGroup(Long ownerUserId, RegisterGroupRequest request) {
        if (groups.findByOwnerUserId(ownerUserId).isPresent()) {
            throw new IllegalArgumentException("A group is already registered for this account");
        }
        Camp camp = camps.findById(request.campId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown camp: " + request.campId()));

        FamilyGroup group = groups.save(new FamilyGroup(ownerUserId, camp.getId(), request.groupName()));
        for (MemberInput member : request.members()) {
            members.save(new FamilyMember(group.getId(), member.nickname(), member.ageBand()));
        }
        return toStatus(group, camp);
    }

    @Transactional(readOnly = true)
    public Optional<FamilyGroupStatus> myGroup(Long ownerUserId) {
        return groups.findByOwnerUserId(ownerUserId).map(this::toStatusLoadingCamp);
    }

    /** The representative's own "I've arrived" tap — one of the two independent confirmations. */
    public FamilyGroupStatus confirmRepresentativeArrival(Long ownerUserId) {
        FamilyGroup group = groups.findByOwnerUserId(ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("No group registered for this account"));
        group.markRepresentativeArrived();
        return toStatusLoadingCamp(group);
    }

    /**
     * Search-not-browse: a blank query returns nothing, never the full list, and the result
     * carries only what reunification needs — never a roster or medical data.
     */
    @Transactional(readOnly = true)
    public List<ReunificationResult> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return groups.findByGroupNameContainingIgnoreCase(query.trim()).stream()
                .map(group -> {
                    Camp camp = camps.findById(group.getCampId()).orElseThrow();
                    return new ReunificationResult(
                            group.getGroupName(), camp.getNameEn(), camp.getNameBn(), statusOf(group));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public CampArrivalsView campArrivals(Long callerUserId, Role callerRole, Long campId) {
        requireCampAccess(callerUserId, callerRole, campId);
        List<CampArrivalGroup> views = groups.findByCampId(campId).stream().map(this::toCampArrivalGroup).toList();
        int arriving = (int) views.stream().filter(g -> g.status() == ArrivalStatus.ARRIVING).count();
        int arrived = (int) views.stream().filter(g -> g.status() == ArrivalStatus.ARRIVED).count();
        return new CampArrivalsView(arriving, arrived, views);
    }

    /** The camp manager's (or coordinator/admin's) confirmation — the other half of dual-source arrival. */
    public CampArrivalGroup confirmManagerArrival(Long callerUserId, Role callerRole, Long campId, Long groupId) {
        requireCampAccess(callerUserId, callerRole, campId);
        FamilyGroup group = groupInCamp(campId, groupId);
        group.markManagerConfirmedArrived();
        return toCampArrivalGroup(group);
    }

    /** Only staff may set a member's medical flag; it is never part of representative-submitted input. */
    public void setMedicalFlag(
            Long callerUserId, Role callerRole, Long campId, Long groupId, Long memberId, boolean flag) {
        requireCampAccess(callerUserId, callerRole, campId);
        groupInCamp(campId, groupId);
        FamilyMember member = members.findById(memberId)
                .filter(m -> m.getFamilyGroupId().equals(groupId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown member: " + memberId));
        member.setMedicalFlag(flag);
    }

    private FamilyGroup groupInCamp(Long campId, Long groupId) {
        FamilyGroup group = groups.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown group: " + groupId));
        if (!group.getCampId().equals(campId)) {
            throw new IllegalArgumentException("Group " + groupId + " is not registered at camp " + campId);
        }
        return group;
    }

    private void requireCampAccess(Long userId, Role role, Long campId) {
        if (role == Role.ADMIN || role == Role.COORDINATOR) {
            return; // whole-operation oversight, same as the realtime camp-topic boundary
        }
        if (role != Role.CAMP_MANAGER || !assignments.existsByUserIdAndCampId(userId, campId)) {
            throw new AccessDeniedException("Not assigned to this camp");
        }
    }

    private FamilyGroupStatus toStatusLoadingCamp(FamilyGroup group) {
        Camp camp = camps.findById(group.getCampId()).orElseThrow();
        return toStatus(group, camp);
    }

    private FamilyGroupStatus toStatus(FamilyGroup group, Camp camp) {
        List<MemberView> memberViews = memberViewsOf(group.getId());
        return new FamilyGroupStatus(
                group.getId(),
                group.getGroupName(),
                camp.getId(),
                camp.getNameEn(),
                camp.getNameBn(),
                memberViews.size(),
                group.isRepresentativeArrived(),
                group.isManagerConfirmedArrived(),
                statusOf(group),
                memberViews);
    }

    private CampArrivalGroup toCampArrivalGroup(FamilyGroup group) {
        List<MemberView> memberViews = memberViewsOf(group.getId());
        return new CampArrivalGroup(
                group.getId(),
                group.getGroupName(),
                memberViews.size(),
                group.isRepresentativeArrived(),
                group.isManagerConfirmedArrived(),
                statusOf(group),
                memberViews);
    }

    private List<MemberView> memberViewsOf(Long groupId) {
        return members.findByFamilyGroupId(groupId).stream()
                .map(m -> new MemberView(m.getId(), m.getNickname(), m.getAgeBand(), m.isMedicalFlag()))
                .toList();
    }

    private ArrivalStatus statusOf(FamilyGroup group) {
        if (group.isRepresentativeArrived() && group.isManagerConfirmedArrived()) {
            return ArrivalStatus.ARRIVED;
        }
        if (group.isRepresentativeArrived() || group.isManagerConfirmedArrived()) {
            return ArrivalStatus.ARRIVING;
        }
        return ArrivalStatus.REGISTERED;
    }
}
