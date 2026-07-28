package bd.dms.proposal;

/** What kind of world-structure change a {@link DisasterProposal} asks for. Each value maps to
 * one {@code DisasterAdminService} method a central authority's approval replays. */
public enum ProposalType {
    DISASTER_CREATE,
    DISASTER_UPDATE,
    DISASTER_CLOSE,
    AFFECTED_AREA_CREATE,
    CAMP_CREATE
}
