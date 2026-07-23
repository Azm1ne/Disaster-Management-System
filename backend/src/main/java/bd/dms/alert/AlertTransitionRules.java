package bd.dms.alert;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The legal alert state graph, as a pure function of (from, to) — mirrors {@code Scenario}'s
 * "pure function of tick" style. {@link AlertService} is the only caller that mutates state; this
 * class only says what is allowed.
 */
public final class AlertTransitionRules {

    private static final Map<AlertStatus, Set<AlertStatus>> LEGAL = new EnumMap<>(AlertStatus.class);

    static {
        LEGAL.put(AlertStatus.NEW, EnumSet.of(AlertStatus.ACKNOWLEDGED));
        LEGAL.put(AlertStatus.ACKNOWLEDGED, EnumSet.of(AlertStatus.IN_PROGRESS));
        LEGAL.put(AlertStatus.IN_PROGRESS, EnumSet.of(AlertStatus.RESOLVED, AlertStatus.ESCALATED));
        LEGAL.put(AlertStatus.RESOLVED, EnumSet.of(AlertStatus.CLOSED));
        LEGAL.put(AlertStatus.ESCALATED, EnumSet.of(AlertStatus.IN_PROGRESS, AlertStatus.CLOSED));
        LEGAL.put(AlertStatus.CLOSED, EnumSet.noneOf(AlertStatus.class));
    }

    private AlertTransitionRules() {}

    public static boolean isLegal(AlertStatus from, AlertStatus to) {
        return LEGAL.getOrDefault(from, Set.of()).contains(to);
    }
}
