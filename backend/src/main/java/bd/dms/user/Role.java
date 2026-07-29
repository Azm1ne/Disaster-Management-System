package bd.dms.user;

/**
 * The eight operational roles of the DMS. {@code CENTRAL_AUTHORITY} (ticket 13) is inbox-only:
 * it gets no shell and no camp/disaster scoping, just two capabilities — list pending disaster
 * proposals and approve/reject them — not a full workspace role like the others.
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
    ADMIN,
    CENTRAL_AUTHORITY
}
