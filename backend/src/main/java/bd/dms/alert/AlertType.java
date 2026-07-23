package bd.dms.alert;

/**
 * What kind of thing is being flagged. Routing is fixed by type: every type routes to the
 * camp's Camp Manager(s) except {@link #SECURITY_INCIDENT}, which goes straight to
 * Coordinator/Admin. Coordinator/Admin always see every alert regardless of type.
 */
public enum AlertType {
    RESOURCE_SHORTAGE,
    MEDICAL_EMERGENCY,
    SECURITY_INCIDENT,
    INFRASTRUCTURE_DAMAGE;

    public boolean routesToCampManager() {
        return this != SECURITY_INCIDENT;
    }
}
