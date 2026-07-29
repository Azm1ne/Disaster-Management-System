package bd.dms.proposal;

import bd.dms.api.DisasterAdminController.CreateAffectedAreaRequest;
import bd.dms.api.DisasterAdminController.CreateCampRequest;
import bd.dms.api.DisasterAdminController.CreateDisasterRequest;
import bd.dms.api.DisasterAdminController.UpdateDisasterRequest;
import bd.dms.world.DisasterAdminService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatches a coordinator's {@link DisasterProposal} to the exact same
 * {@link DisasterAdminService} method the direct admin path uses, once a central authority
 * approves it — the proposal path and the direct-admin path can never diverge in behaviour.
 */
@Service
@Transactional
public class ProposalService {

    private final DisasterProposalRepository proposals;
    private final DisasterAdminService adminService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ProposalService(
            DisasterProposalRepository proposals, DisasterAdminService adminService, ObjectMapper objectMapper,
            Validator validator) {
        this.proposals = proposals;
        this.adminService = adminService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /** Files a new proposal. {@code targetDisasterId} must be null for DISASTER_CREATE (no
     * disaster exists yet) and non-null for every other type (they all act on one). */
    public DisasterProposal propose(
            ProposalType type, Long targetDisasterId, String payloadJson, Long proposedByUserId) {
        boolean requiresTarget = type != ProposalType.DISASTER_CREATE;
        if (requiresTarget && targetDisasterId == null) {
            throw new IllegalArgumentException(type + " proposals require a targetDisasterId");
        }
        if (!requiresTarget && targetDisasterId != null) {
            throw new IllegalArgumentException("DISASTER_CREATE proposals must not set targetDisasterId");
        }
        return proposals.save(new DisasterProposal(type, targetDisasterId, payloadJson, proposedByUserId));
    }

    @Transactional(readOnly = true)
    public List<DisasterProposal> listPending() {
        return proposals.findByStatus(ProposalStatus.PENDING);
    }

    /** Approves a PENDING proposal, replaying its payload through the matching
     * {@code DisasterAdminService} method, then marks it approved. Throws
     * {@code IllegalStateException} (via {@code DisasterProposal.approve}) if the proposal has
     * already been resolved. */
    public DisasterProposal approve(Long proposalId, Long reviewedByUserId, String reviewNote) {
        DisasterProposal proposal = proposals.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown proposal: " + proposalId));

        // Fail before mutating the world if this proposal was already resolved — the entity's
        // own guard in approve()/reject() is the real enforcement, this just avoids doing the
        // DisasterAdminService write at all on an already-resolved proposal.
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new IllegalStateException("Proposal " + proposalId + " has already been reviewed");
        }
        apply(proposal, reviewedByUserId);
        proposal.approve(reviewedByUserId, reviewNote);
        return proposals.save(proposal);
    }

    /** Rejects a PENDING proposal without touching world state. Throws
     * {@code IllegalStateException} if the proposal has already been resolved. */
    public DisasterProposal reject(Long proposalId, Long reviewedByUserId, String reviewNote) {
        DisasterProposal proposal = proposals.findById(proposalId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown proposal: " + proposalId));

        proposal.reject(reviewedByUserId, reviewNote);
        return proposals.save(proposal);
    }

    private void apply(DisasterProposal proposal, Long actorUserId) {
        try {
            switch (proposal.getProposalType()) {
                case DISASTER_CREATE -> {
                    CreateDisasterRequest request = read(proposal, CreateDisasterRequest.class);
                    adminService.createDisaster(
                            request.code(), request.type(), request.nameEn(), request.nameBn(),
                            request.geometry(), actorUserId);
                }
                case DISASTER_UPDATE -> {
                    UpdateDisasterRequest request = read(proposal, UpdateDisasterRequest.class);
                    adminService.updateDisaster(
                            proposal.getTargetDisasterId(), request.nameEn(), request.nameBn(),
                            request.geometry(), actorUserId);
                }
                case DISASTER_CLOSE -> adminService.closeDisaster(proposal.getTargetDisasterId(), actorUserId);
                case AFFECTED_AREA_CREATE -> {
                    CreateAffectedAreaRequest request = read(proposal, CreateAffectedAreaRequest.class);
                    adminService.createAffectedArea(
                            proposal.getTargetDisasterId(), request.nameEn(), request.nameBn(),
                            request.geometry(), actorUserId);
                }
                case CAMP_CREATE -> {
                    CreateCampRequest request = read(proposal, CreateCampRequest.class);
                    adminService.createCamp(
                            proposal.getTargetDisasterId(), request.code(), request.nameEn(), request.nameBn(),
                            request.lat(), request.lng(), request.capacity(), request.initialPopulation(),
                            actorUserId);
                }
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid proposal payload: " + proposal.getPayload(), e);
        }
    }

    /** Deserializes the payload and runs it through the same Bean Validation constraints
     * {@code DisasterAdminController}'s {@code @Valid @RequestBody} would enforce on the direct
     * admin path — {@code ObjectMapper.readValue} alone does not trigger {@code @NotBlank}/
     * {@code @PositiveOrZero} on the reused request records, so this closes that gap explicitly. */
    private <T> T read(DisasterProposal proposal, Class<T> type) throws JsonProcessingException {
        T request = objectMapper.readValue(proposal.getPayload(), type);
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException("Invalid proposal payload: " + message);
        }
        return request;
    }
}
