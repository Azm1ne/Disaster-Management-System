package bd.dms.anomaly;

/** Which detector produced a given {@link AnomalyFlag} — determines what {@code subjectIds}
 * point at (allocation-decision ids / family-group ids / donation ids) and which static
 * {@code innocentExplanation} was attached. */
public enum AnomalyDetectorType {
    ALLOCATION_BURST,
    DUPLICATE_REGISTRATION,
    DONATION_PATTERN
}
