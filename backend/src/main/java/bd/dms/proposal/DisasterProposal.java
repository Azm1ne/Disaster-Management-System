package bd.dms.proposal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A coordinator's request to create/update/close a disaster or to create an affected area or
 * camp, awaiting a central-authority decision. {@code payload} is JSON matching whatever DTO
 * the corresponding direct admin endpoint accepts, so approval deserializes it and replays it
 * through the exact same {@code DisasterAdminService} method the direct admin path uses —
 * there is only ever one write path for a given kind of world-structure change.
 */
@Entity
@Table(name = "disaster_proposals")
public class DisasterProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposal_type", nullable = false)
    private ProposalType proposalType;

    /** Null only for {@link ProposalType#DISASTER_CREATE}, which has no existing disaster yet. */
    @Column(name = "target_disaster_id")
    private Long targetDisasterId;

    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProposalStatus status;

    @Column(name = "proposed_by_user_id", nullable = false)
    private Long proposedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reviewed_by_user_id")
    private Long reviewedByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note")
    private String reviewNote;

    protected DisasterProposal() {
        // for JPA
    }

    public DisasterProposal(
            ProposalType proposalType, Long targetDisasterId, String payload, Long proposedByUserId) {
        this.proposalType = proposalType;
        this.targetDisasterId = targetDisasterId;
        this.payload = payload;
        this.status = ProposalStatus.PENDING;
        this.proposedByUserId = proposedByUserId;
        this.createdAt = Instant.now();
    }

    /** A proposal is reviewed exactly once — throws if it has already left PENDING, matching
     * the {@code AnomalyFlag.dispose} idempotency-guard convention. */
    public void approve(Long reviewedByUserId, String reviewNote) {
        requirePending();
        this.status = ProposalStatus.APPROVED;
        this.reviewedByUserId = reviewedByUserId;
        this.reviewNote = reviewNote;
        this.reviewedAt = Instant.now();
    }

    public void reject(Long reviewedByUserId, String reviewNote) {
        requirePending();
        this.status = ProposalStatus.REJECTED;
        this.reviewedByUserId = reviewedByUserId;
        this.reviewNote = reviewNote;
        this.reviewedAt = Instant.now();
    }

    private void requirePending() {
        if (status != ProposalStatus.PENDING) {
            throw new IllegalStateException("Proposal " + id + " has already been reviewed");
        }
    }

    public Long getId() {
        return id;
    }

    public ProposalType getProposalType() {
        return proposalType;
    }

    public Long getTargetDisasterId() {
        return targetDisasterId;
    }

    public String getPayload() {
        return payload;
    }

    public ProposalStatus getStatus() {
        return status;
    }

    public Long getProposedByUserId() {
        return proposedByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getReviewedByUserId() {
        return reviewedByUserId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }
}
