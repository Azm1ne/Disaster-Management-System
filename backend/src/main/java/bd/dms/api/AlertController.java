package bd.dms.api;

import bd.dms.alert.Alert;
import bd.dms.alert.AlertService;
import bd.dms.alert.AlertStatus;
import bd.dms.alert.AlertTransition;
import bd.dms.alert.AlertType;
import bd.dms.alert.dto.AlertDetail;
import bd.dms.alert.dto.AlertSummary;
import bd.dms.alert.dto.NoteView;
import bd.dms.alert.dto.TransitionView;
import bd.dms.note.Note;
import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The alert lifecycle surface: raise, list/read what the caller is entitled to, drive
 * transitions, and add case notes. Role/camp entitlement is enforced in {@link AlertService};
 * this controller only resolves the caller and shapes responses.
 */
@RestController
@RequestMapping("/alerts")
public class AlertController {

    public record CreateAlertRequest(
            @NotNull AlertType type, @NotNull Long campId, @NotBlank String description) {}

    public record TransitionRequest(@NotNull AlertStatus toStatus, String note) {}

    public record NoteRequest(@NotBlank String body) {}

    private final AlertService alertService;
    private final UserRepository users;

    public AlertController(AlertService alertService, UserRepository users) {
        this.alertService = alertService;
        this.users = users;
    }

    @GetMapping
    public List<AlertSummary> list(Authentication authentication) {
        AppUser actor = actor(authentication);
        return alertService.visibleTo(actor).stream().map(alert -> toSummary(alert, actor)).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertDetail> detail(@PathVariable Long id, Authentication authentication) {
        AppUser actor = actor(authentication);
        return alertService.visibleDetail(actor, id)
                .map(alert -> ResponseEntity.ok(toDetail(alert, actor)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public AlertSummary create(@Valid @RequestBody CreateAlertRequest request, Authentication authentication) {
        AppUser actor = actor(authentication);
        Alert alert = alertService.raise(actor, request.type(), request.campId(), request.description());
        return toSummary(alert, actor);
    }

    @PostMapping("/demo")
    public AlertSummary demo(Authentication authentication) {
        AppUser actor = actor(authentication);
        return toSummary(alertService.demoRaise(actor), actor);
    }

    @PostMapping("/{id}/transition")
    public AlertSummary transition(
            @PathVariable Long id, @Valid @RequestBody TransitionRequest request, Authentication authentication) {
        AppUser actor = actor(authentication);
        Alert alert = alertService.transition(actor, id, request.toStatus(), request.note());
        return toSummary(alert, actor);
    }

    @PostMapping("/{id}/notes")
    public NoteView note(
            @PathVariable Long id, @Valid @RequestBody NoteRequest request, Authentication authentication) {
        Note note = alertService.addNote(actor(authentication), id, request.body());
        return toNoteView(note);
    }

    private AppUser actor(Authentication authentication) {
        return users.findByUsername(authentication.getName()).orElseThrow();
    }

    private AlertSummary toSummary(Alert alert, AppUser actor) {
        return new AlertSummary(
                alert.getId(),
                alert.getType(),
                alert.getStatus(),
                alert.getCampId(),
                alert.getResourceType(),
                alert.getDescription(),
                alert.getRaisedByUserId(),
                alert.getRaisedAtTick(),
                alert.getSlaDeadlineTick(),
                alertService.canAct(actor, alert),
                alert.getCreatedAt(),
                alert.getUpdatedAt());
    }

    private AlertDetail toDetail(Alert alert, AppUser actor) {
        List<TransitionView> transitionViews = alertService.transitionsFor(alert.getId()).stream()
                .map(this::toTransitionView)
                .toList();
        List<NoteView> noteViews = alertService.notesFor(alert.getId()).stream().map(this::toNoteView).toList();
        return new AlertDetail(toSummary(alert, actor), transitionViews, noteViews);
    }

    private TransitionView toTransitionView(AlertTransition transition) {
        return new TransitionView(
                transition.getFromStatus(),
                transition.getToStatus(),
                transition.getActorUserId(),
                transition.getNote(),
                transition.getAtTick(),
                transition.getCreatedAt());
    }

    private NoteView toNoteView(Note note) {
        return new NoteView(note.getAuthorUserId(), note.getBody(), note.getCreatedAt());
    }
}
