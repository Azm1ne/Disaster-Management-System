package bd.dms.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bd.dms.anomaly.DuplicateRegistrationDetector.MemberKey;
import bd.dms.family.FamilyGroup;
import bd.dms.family.FamilyGroupRepository;
import bd.dms.family.FamilyMember;
import bd.dms.family.FamilyMemberRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DuplicateRegistrationDetectorTest {

    @Mock
    private FamilyGroupRepository groups;

    @Mock
    private FamilyMemberRepository members;

    @Mock
    private AnomalyFlagRepository flags;

    @Mock
    private FamilyGroup newGroup;

    @Mock
    private FamilyGroup candidateGroup;

    private DuplicateRegistrationDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DuplicateRegistrationDetector(groups, members, flags);
        lenient().when(newGroup.getId()).thenReturn(1L);
        lenient().when(candidateGroup.getId()).thenReturn(2L);
    }

    @Test
    void normalizedLevenshteinSimilarityIsOneForIdenticalStrings() {
        assertThat(DuplicateRegistrationDetector.normalizedLevenshteinSimilarity("rahman family", "rahman family"))
                .isEqualTo(1.0);
    }

    @Test
    void normalizedLevenshteinSimilarityIsNearZeroForCompletelyDifferentStrings() {
        assertThat(DuplicateRegistrationDetector.normalizedLevenshteinSimilarity("aaaaaaaaaa", "zzzzzzzzzz"))
                .isCloseTo(0.0, offset(0.001));
    }

    @Test
    void normalizedLevenshteinSimilarityIsPartialForACloseTypo() {
        // "rahman family" -> "rahman familly" is a single-character insertion, 14-char string.
        assertThat(DuplicateRegistrationDetector.normalizedLevenshteinSimilarity("rahman family", "rahman familly"))
                .isCloseTo(1.0 - 1.0 / 14.0, offset(0.001));
    }

    @Test
    void similarityCombinesNameAndOverlapEqually() {
        double sim = DuplicateRegistrationDetector.similarity("rahman family", List.of(), "rahman family", List.of());
        assertThat(sim).isCloseTo(0.5, offset(0.001)); // name=1.0, overlap=0 (empty rosters) -> 0.5*1 + 0.5*0
    }

    @Test
    void overlapRatioIsOneWhenRostersAreIdentical() {
        List<MemberKey> a = List.of(new MemberKey("karim", "ADULT"), new MemberKey("amina", "CHILD"));
        List<MemberKey> b = List.of(new MemberKey("karim", "ADULT"), new MemberKey("amina", "CHILD"));
        assertThat(DuplicateRegistrationDetector.overlapRatio(a, b)).isEqualTo(1.0);
    }

    @Test
    void overlapRatioIsZeroWhenRostersShareNoMembers() {
        List<MemberKey> a = List.of(new MemberKey("karim", "ADULT"));
        List<MemberKey> b = List.of(new MemberKey("jamal", "ADULT"));
        assertThat(DuplicateRegistrationDetector.overlapRatio(a, b)).isEqualTo(0.0);
    }

    @Test
    void overlapRatioIsCaseInsensitiveOnNicknameButExactOnAgeBand() {
        List<MemberKey> a = List.of(new MemberKey("karim", "ADULT"));
        List<MemberKey> b = List.of(new MemberKey("karim", "CHILD"));
        assertThat(DuplicateRegistrationDetector.overlapRatio(a, b)).isEqualTo(0.0);
    }

    @Test
    void scanNewGroupCreatesAFlagForANearDuplicatePair() {
        FamilyMember newMember = new FamilyMember(1L, "Karim", "ADULT");
        FamilyMember candidateMember = new FamilyMember(2L, "Karim", "ADULT");

        when(newGroup.getGroupName()).thenReturn("Rahman Family");
        when(candidateGroup.getGroupName()).thenReturn("Rahman Familly");
        when(groups.findAll()).thenReturn(List.of(newGroup, candidateGroup));
        when(members.findByFamilyGroupId(2L)).thenReturn(List.of(candidateMember));
        when(flags.findByDetectorTypeOrderByCreatedAtDesc(AnomalyDetectorType.DUPLICATE_REGISTRATION))
                .thenReturn(List.of());
        when(flags.save(any(AnomalyFlag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        detector.scanNewGroup(newGroup, List.of(newMember));

        verify(flags).save(any(AnomalyFlag.class));
    }

    @Test
    void scanNewGroupCreatesNoFlagForAClearlyDifferentPair() {
        FamilyMember newMember = new FamilyMember(1L, "Karim", "ADULT");
        FamilyMember candidateMember = new FamilyMember(2L, "Jamal", "CHILD");

        when(newGroup.getGroupName()).thenReturn("Rahman Family");
        when(candidateGroup.getGroupName()).thenReturn("Chowdhury Household");
        when(groups.findAll()).thenReturn(List.of(newGroup, candidateGroup));
        when(members.findByFamilyGroupId(2L)).thenReturn(List.of(candidateMember));

        detector.scanNewGroup(newGroup, List.of(newMember));

        verify(flags, never()).save(any(AnomalyFlag.class));
    }

    @Test
    void scanNewGroupSkipsAPairAlreadyFlaggedOpen() {
        FamilyMember newMember = new FamilyMember(1L, "Karim", "ADULT");
        FamilyMember candidateMember = new FamilyMember(2L, "Karim", "ADULT");

        when(newGroup.getGroupName()).thenReturn("Rahman Family");
        when(candidateGroup.getGroupName()).thenReturn("Rahman Familly");
        when(groups.findAll()).thenReturn(List.of(newGroup, candidateGroup));
        when(members.findByFamilyGroupId(2L)).thenReturn(List.of(candidateMember));
        AnomalyFlag existing = new AnomalyFlag(
                AnomalyDetectorType.DUPLICATE_REGISTRATION, 0.9, "existing", "explanation", List.of(1L, 2L), null);
        when(flags.findByDetectorTypeOrderByCreatedAtDesc(AnomalyDetectorType.DUPLICATE_REGISTRATION))
                .thenReturn(List.of(existing));

        detector.scanNewGroup(newGroup, List.of(newMember));

        verify(flags, never()).save(any(AnomalyFlag.class));
    }
}
