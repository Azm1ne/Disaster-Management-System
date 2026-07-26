package bd.dms.anomaly;

/** Lifecycle of an {@link AnomalyFlag}: every flag is created {@code OPEN} and is reviewed
 * exactly once, ending at {@code CONFIRMED} or {@code DISMISSED} — see {@link
 * AnomalyFlag#dispose}. */
public enum AnomalyFlagStatus {
    OPEN,
    CONFIRMED,
    DISMISSED
}
