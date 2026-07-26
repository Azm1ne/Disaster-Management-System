package bd.dms.anomaly;

import bd.dms.family.FamilyGroup;
import bd.dms.family.FamilyGroupRepository;
import bd.dms.family.FamilyMember;
import bd.dms.family.FamilyMemberRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Fuzzy-matches a newly registered {@link FamilyGroup} against every other existing group (name
 * similarity via Levenshtein + roster overlap) and flags likely duplicate registrations. Runs
 * synchronously right after {@code FamilyService.registerGroup} persists the new group and its
 * members. Never mutates {@code family_groups}/{@code family_members} — only reads them and
 * writes a new {@link AnomalyFlag}.
 */
@Component
public class DuplicateRegistrationDetector {

    static final double SIMILARITY_THRESHOLD = 0.6;

    private static final String INNOCENT_EXPLANATION =
            "Families displaced together often share common local names and similar household "
                    + "compositions — this may be two distinct families rather than a duplicate "
                    + "registration.";

    private final FamilyGroupRepository groups;
    private final FamilyMemberRepository members;
    private final AnomalyFlagRepository flags;

    public DuplicateRegistrationDetector(
            FamilyGroupRepository groups, FamilyMemberRepository members, AnomalyFlagRepository flags) {
        this.groups = groups;
        this.members = members;
        this.flags = flags;
    }

    /** One person's identity for roster-overlap purposes: case-insensitive nickname + exact age band. */
    public record MemberKey(String nicknameLower, String ageBand) {}

    /** Pure, DB-free similarity score in [0, 1]: half name similarity, half roster overlap. */
    public static double similarity(
            String nameA, List<MemberKey> membersA, String nameB, List<MemberKey> membersB) {
        double nameSim = normalizedLevenshteinSimilarity(nameA.trim().toLowerCase(), nameB.trim().toLowerCase());
        double memberOverlap = overlapRatio(membersA, membersB);
        return 0.5 * nameSim + 0.5 * memberOverlap;
    }

    static double normalizedLevenshteinSimilarity(String a, String b) {
        int distance = levenshteinDistance(a, b);
        return 1.0 - distance / (double) Math.max(1, Math.max(a.length(), b.length()));
    }

    private static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    static double overlapRatio(List<MemberKey> membersA, List<MemberKey> membersB) {
        Set<MemberKey> setA = new HashSet<>(membersA);
        Set<MemberKey> setB = new HashSet<>(membersB);
        Set<MemberKey> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        return intersection.size() / (double) Math.max(1, Math.min(setA.size(), setB.size()));
    }

    public void scanNewGroup(FamilyGroup newGroup, List<FamilyMember> newMembers) {
        List<MemberKey> newKeys = toKeys(newMembers);
        List<FamilyGroup> candidates = groups.findAll().stream()
                .filter(g -> !g.getId().equals(newGroup.getId()))
                .toList();

        for (FamilyGroup candidate : candidates) {
            List<MemberKey> candidateKeys = toKeys(members.findByFamilyGroupId(candidate.getId()));
            double score = similarity(newGroup.getGroupName(), newKeys, candidate.getGroupName(), candidateKeys);
            if (score < SIMILARITY_THRESHOLD) {
                continue;
            }
            if (alreadyFlagged(newGroup.getId(), candidate.getId())) {
                continue;
            }
            String summary = "Group '%s' is %.0f%% similar to existing group '%s' (name + roster overlap)"
                    .formatted(newGroup.getGroupName(), score * 100, candidate.getGroupName());
            AnomalyFlag flag = new AnomalyFlag(
                    AnomalyDetectorType.DUPLICATE_REGISTRATION,
                    score,
                    summary,
                    INNOCENT_EXPLANATION,
                    List.of(newGroup.getId(), candidate.getId()),
                    null);
            flags.save(flag);
        }
    }

    private boolean alreadyFlagged(Long groupIdA, Long groupIdB) {
        return flags.findByDetectorTypeOrderByCreatedAtDesc(AnomalyDetectorType.DUPLICATE_REGISTRATION).stream()
                .filter(f -> f.getStatus() == AnomalyFlagStatus.OPEN || f.getStatus() == AnomalyFlagStatus.CONFIRMED)
                .anyMatch(f -> f.getSubjectIds().contains(groupIdA) && f.getSubjectIds().contains(groupIdB));
    }

    private List<MemberKey> toKeys(List<FamilyMember> memberRows) {
        List<MemberKey> keys = new ArrayList<>();
        for (FamilyMember m : memberRows) {
            keys.add(new MemberKey(m.getNickname().toLowerCase(), m.getAgeBand()));
        }
        return keys;
    }
}
