package bd.dms.proposal.dto;

import bd.dms.proposal.ProposalStatus;
import bd.dms.proposal.ProposalType;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** A proposal as seen by either the coordinator who filed it or the central authority
 * reviewing it. {@code payload} is parsed back to JSON for the response rather than left as
 * a raw string, matching the geometry-as-JsonNode convention used for admin views. */
public record ProposalView(
        Long id,
        ProposalType proposalType,
        Long targetDisasterId,
        JsonNode payload,
        ProposalStatus status,
        Long proposedByUserId,
        Instant createdAt,
        Long reviewedByUserId,
        Instant reviewedAt,
        String reviewNote) {}
