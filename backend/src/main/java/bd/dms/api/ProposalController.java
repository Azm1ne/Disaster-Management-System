package bd.dms.api;

import bd.dms.proposal.DisasterProposal;
import bd.dms.proposal.ProposalService;
import bd.dms.proposal.ProposalType;
import bd.dms.proposal.dto.ProposalView;
import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The minimal proposal/approval inbox: a coordinator files a proposed disaster/affected-area/
 * camp change here, a central authority approves or rejects it. Every mutation is delegated to
 * {@link ProposalService}, which replays an approved payload through {@code DisasterAdminService}
 * — the same write path the direct admin controller uses. Role gating for every path here lives
 * in {@code SecurityConfig}, not in this controller.
 */
@RestController
@RequestMapping("/proposals")
public class ProposalController {

    public record ProposeRequest(
            @NotNull ProposalType proposalType, Long targetDisasterId, @NotNull JsonNode payload) {}

    public record ReviewRequest(String reviewNote) {}

    private final ProposalService proposalService;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    public ProposalController(ProposalService proposalService, UserRepository users, ObjectMapper objectMapper) {
        this.proposalService = proposalService;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ProposalView propose(@Valid @RequestBody ProposeRequest request, Authentication authentication) {
        DisasterProposal proposal = proposalService.propose(
                request.proposalType(), request.targetDisasterId(), payloadOf(request.payload()),
                actor(authentication).getId());
        return toView(proposal);
    }

    @GetMapping
    public List<ProposalView> listPending() {
        return proposalService.listPending().stream().map(this::toView).toList();
    }

    @PostMapping("/{id}/approve")
    public ProposalView approve(
            @PathVariable Long id, @RequestBody(required = false) ReviewRequest request, Authentication authentication) {
        DisasterProposal proposal = proposalService.approve(
                id, actor(authentication).getId(), reviewNoteOf(request));
        return toView(proposal);
    }

    @PostMapping("/{id}/reject")
    public ProposalView reject(
            @PathVariable Long id, @RequestBody(required = false) ReviewRequest request, Authentication authentication) {
        DisasterProposal proposal = proposalService.reject(
                id, actor(authentication).getId(), reviewNoteOf(request));
        return toView(proposal);
    }

    private static String reviewNoteOf(ReviewRequest request) {
        return request == null ? null : request.reviewNote();
    }

    private AppUser actor(Authentication authentication) {
        return users.findByUsername(authentication.getName()).orElseThrow();
    }

    /** Re-serializes the request's parsed JSON payload back to text for storage — the entity
     * column is a JSON string, matching {@code DisasterProposal.payload}'s NOT NULL text shape. */
    private String payloadOf(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize proposal payload", e);
        }
    }

    private ProposalView toView(DisasterProposal proposal) {
        return new ProposalView(
                proposal.getId(), proposal.getProposalType(), proposal.getTargetDisasterId(),
                payloadTree(proposal.getPayload()),
                proposal.getStatus(), proposal.getProposedByUserId(), proposal.getCreatedAt(),
                proposal.getReviewedByUserId(), proposal.getReviewedAt(), proposal.getReviewNote());
    }

    private JsonNode payloadTree(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid proposal payload JSON: " + payload, e);
        }
    }
}
