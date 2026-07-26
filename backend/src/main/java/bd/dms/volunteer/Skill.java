package bd.dms.volunteer;

import bd.dms.alert.AlertType;

/**
 * The four volunteer skills, one per {@link AlertType} — a fixed 1:1 mapping so every open alert
 * that reaches {@link VolunteerTaskGenerationService} deterministically implies exactly one
 * required skill.
 */
public enum Skill {
    MEDICAL,
    LOGISTICS,
    SECURITY,
    ENGINEERING;

    public static Skill forAlertType(AlertType type) {
        return switch (type) {
            case MEDICAL_EMERGENCY -> MEDICAL;
            case RESOURCE_SHORTAGE -> LOGISTICS;
            case SECURITY_INCIDENT -> SECURITY;
            case INFRASTRUCTURE_DAMAGE -> ENGINEERING;
        };
    }
}
