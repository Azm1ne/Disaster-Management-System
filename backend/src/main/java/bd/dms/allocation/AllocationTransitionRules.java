package bd.dms.allocation;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The legal allocation state graph, as a pure function of (from, to) — mirrors
 * {@code AlertTransitionRules}'s style. Every decided status (APPROVED/MODIFIED/REJECTED) is
 * terminal: once a coordinator has acted, a fresh recommendation for the same camp pair is a new
 * row, not a re-opened one.
 */
public final class AllocationTransitionRules {

    private static final Map<AllocationStatus, Set<AllocationStatus>> LEGAL = new EnumMap<>(AllocationStatus.class);

    static {
        LEGAL.put(
                AllocationStatus.RECOMMENDED,
                EnumSet.of(AllocationStatus.APPROVED, AllocationStatus.MODIFIED, AllocationStatus.REJECTED));
        LEGAL.put(AllocationStatus.APPROVED, EnumSet.noneOf(AllocationStatus.class));
        LEGAL.put(AllocationStatus.MODIFIED, EnumSet.noneOf(AllocationStatus.class));
        LEGAL.put(AllocationStatus.REJECTED, EnumSet.noneOf(AllocationStatus.class));
    }

    private AllocationTransitionRules() {}

    public static boolean isLegal(AllocationStatus from, AllocationStatus to) {
        return LEGAL.getOrDefault(from, Set.of()).contains(to);
    }
}
