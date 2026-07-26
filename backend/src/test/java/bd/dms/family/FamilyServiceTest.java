package bd.dms.family;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bd.dms.anomaly.DuplicateRegistrationDetector;
import bd.dms.family.dto.FamilyGroupStatus;
import bd.dms.family.dto.MemberInput;
import bd.dms.family.dto.RegisterGroupRequest;
import bd.dms.world.Camp;
import bd.dms.world.CampAssignmentRepository;
import bd.dms.world.CampRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers {@code registerGroup}'s hook into {@link DuplicateRegistrationDetector}, added in ticket
 * 11 — the detector is mocked, so this asserts only that it is invoked with the saved group/members
 * and that {@code registerGroup} still returns successfully, without touching any pre-existing
 * registration behavior (there was no prior test file for this class).
 */
@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {

    @Mock
    private FamilyGroupRepository groups;

    @Mock
    private FamilyMemberRepository members;

    @Mock
    private CampRepository camps;

    @Mock
    private CampAssignmentRepository assignments;

    @Mock
    private DuplicateRegistrationDetector duplicateDetector;

    @Mock
    private Camp camp;

    private FamilyService familyService;

    @BeforeEach
    void setUp() {
        familyService = new FamilyService(groups, members, camps, assignments, duplicateDetector);
        lenient().when(camp.getId()).thenReturn(10L);
        lenient().when(camp.getNameEn()).thenReturn("Camp Ten");
        lenient().when(camp.getNameBn()).thenReturn("ক্যাম্প টেন");
    }

    @Test
    void registerGroupSavesTheGroupAndInvokesTheDuplicateDetector() {
        when(groups.findByOwnerUserId(1L)).thenReturn(Optional.empty());
        when(camps.findById(10L)).thenReturn(Optional.of(camp));
        FamilyGroup savedGroup = new FamilyGroup(1L, 10L, "Rahman Family");
        when(groups.save(any(FamilyGroup.class))).thenReturn(savedGroup);
        when(members.save(any(FamilyMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(members.findByFamilyGroupId(any())).thenReturn(List.of());

        RegisterGroupRequest request = new RegisterGroupRequest(
                10L, "Rahman Family", List.of(new MemberInput("Karim", "ADULT")));

        FamilyGroupStatus status = familyService.registerGroup(1L, request);

        assertThat(status.groupName()).isEqualTo("Rahman Family");
        verify(duplicateDetector).scanNewGroup(any(FamilyGroup.class), anyList());
    }
}
