package bd.dms.user;

/**
 * The seven operational roles of the DMS. The central authority is a later, minimal
 * inbox and is intentionally not a role here (see the foundation spec).
 *
 * <p>The enum name is the single source of truth for authority strings: Spring Security
 * authorities are {@code "ROLE_" + name()} and the JWT {@code role} claim carries {@code name()}.
 */
public enum Role {
    COORDINATOR,
    CAMP_MANAGER,
    DONOR,
    VOLUNTEER,
    VICTIM,
    NGO,
    ADMIN
}
