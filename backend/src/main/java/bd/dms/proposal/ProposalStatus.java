package bd.dms.proposal;

/** Lifecycle of a {@link DisasterProposal}: PENDING until a central authority acts, then a
 * terminal state. A resolved proposal cannot be acted on again. */
public enum ProposalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
