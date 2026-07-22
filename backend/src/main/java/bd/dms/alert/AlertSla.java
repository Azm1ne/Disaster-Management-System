package bd.dms.alert;

import java.util.EnumMap;
import java.util.Map;

/**
 * Per-type SLA fuse, in simulation ticks. Medical emergencies and security incidents get a
 * short fuse (fast escalation); resource shortages and infrastructure damage a longer one —
 * they are rarely instantly life-threatening the way the other two are.
 */
public final class AlertSla {

    private static final Map<AlertType, Long> THRESHOLD_TICKS = new EnumMap<>(AlertType.class);

    static {
        THRESHOLD_TICKS.put(AlertType.MEDICAL_EMERGENCY, 2L);
        THRESHOLD_TICKS.put(AlertType.SECURITY_INCIDENT, 2L);
        THRESHOLD_TICKS.put(AlertType.RESOURCE_SHORTAGE, 5L);
        THRESHOLD_TICKS.put(AlertType.INFRASTRUCTURE_DAMAGE, 5L);
    }

    private AlertSla() {}

    public static long thresholdTicks(AlertType type) {
        return THRESHOLD_TICKS.get(type);
    }
}
