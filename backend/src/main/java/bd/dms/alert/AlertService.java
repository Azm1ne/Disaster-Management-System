package bd.dms.alert;

import bd.dms.note.Note;
import bd.dms.note.NoteService;
import bd.dms.note.NoteSubjectType;
import bd.dms.sim.SimulationEngine;
import bd.dms.user.AppUser;
import bd.dms.user.Role;
import bd.dms.world.Camp;
import bd.dms.world.CampAssignmentRepository;
import bd.dms.world.CampRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sole writer of alert state (mirrors {@link SimulationEngine} being the sole writer of camp
 * state). Routing is fixed by {@link AlertType#routesToCampManager()}: every alert is visible to
 * Coordinator/Admin; camp-scoped types are additionally visible to — and actionable by — the
 * camp's assigned Camp Manager(s). Legality of a transition is delegated to
 * {@link AlertTransitionRules}; who may perform it is enforced here.
 */
@Service
public class AlertService {

    private final AlertRepository alerts;
    private final AlertTransitionRepository transitions;
    private final CampRepository camps;
    private final CampAssignmentRepository assignments;
    private final NoteService notes;
    private final SimulationEngine engine;
    private final ApplicationEventPublisher events;

    public AlertService(
            AlertRepository alerts,
            AlertTransitionRepository transitions,
            CampRepository camps,
            CampAssignmentRepository assignments,
            NoteService notes,
            SimulationEngine engine,
            ApplicationEventPublisher events) {
        this.alerts = alerts;
        this.transitions = transitions;
        this.camps = camps;
        this.assignments = assignments;
        this.notes = notes;
        this.engine = engine;
        this.events = events;
    }

    @Transactional
    public Alert raise(AppUser actor, AlertType type, Long campId, String description) {
        Camp camp = camps.findById(campId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown camp: " + campId));
        if (!isOversight(actor) && !managesCamp(actor, camp.getId())) {
            throw new AccessDeniedException("Not entitled to raise an alert on this camp");
        }
        long tick = engine.currentTick();
        Alert alert = alerts.save(
                new Alert(type, camp.getId(), description, actor.getId(), tick, tick + AlertSla.thresholdTicks(type)));
        events.publishEvent(new AlertChangedEvent(alert.getId()));
        return alert;
    }

    @Transactional
    public Alert demoRaise(AppUser actor) {
        if (!isOversight(actor)) {
            throw new AccessDeniedException("Only Coordinator/Admin can trigger a demo alert");
        }
        List<Camp> allCamps = camps.findAll();
        if (allCamps.isEmpty()) {
            throw new IllegalStateException("No camps to raise a demo alert against");
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Camp camp = allCamps.get(random.nextInt(allCamps.size()));
        AlertType type = AlertType.values()[random.nextInt(AlertType.values().length)];
        return raise(actor, type, camp.getId(), demoDescription(type));
    }

    @Transactional
    public Alert transition(AppUser actor, Long alertId, AlertStatus to, String note) {
        Alert alert = alerts.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown alert: " + alertId));
        if (!canAct(actor, alert)) {
            throw new AccessDeniedException("Not entitled to act on this alert");
        }
        if (!AlertTransitionRules.isLegal(alert.getStatus(), to)) {
            throw new IllegalArgumentException(
                    "Illegal transition: " + alert.getStatus() + " -> " + to);
        }
        AlertStatus from = alert.getStatus();
        alert.setStatus(to);
        transitions.save(new AlertTransition(alert.getId(), from, to, actor.getId(), note, engine.currentTick()));
        events.publishEvent(new AlertChangedEvent(alert.getId()));
        return alert;
    }

    @Transactional
    public List<Long> escalateStale(long currentTick) {
        List<Alert> stale = alerts.findByStatusIn(List.of(AlertStatus.NEW, AlertStatus.ACKNOWLEDGED)).stream()
                .filter(alert -> alert.getSlaDeadlineTick() <= currentTick)
                .toList();
        for (Alert alert : stale) {
            AlertStatus from = alert.getStatus();
            alert.setStatus(AlertStatus.ESCALATED);
            transitions.save(new AlertTransition(
                    alert.getId(), from, AlertStatus.ESCALATED, null, "SLA breached", currentTick));
            events.publishEvent(new AlertChangedEvent(alert.getId()));
        }
        return stale.stream().map(Alert::getId).toList();
    }

    @Transactional
    public Note addNote(AppUser actor, Long alertId, String body) {
        Alert alert = alerts.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown alert: " + alertId));
        if (!canAct(actor, alert)) {
            throw new AccessDeniedException("Not entitled to note on this alert");
        }
        Note saved = notes.add(NoteSubjectType.ALERT, alertId, actor.getId(), body);
        events.publishEvent(new AlertChangedEvent(alertId));
        return saved;
    }

    public List<Alert> visibleTo(AppUser actor) {
        if (isOversight(actor)) {
            return alerts.findAll();
        }
        if (actor.getRole() != Role.CAMP_MANAGER) {
            return List.of();
        }
        List<Long> campIds = assignments.findByUserId(actor.getId()).stream()
                .map(bd.dms.world.CampAssignment::getCampId)
                .toList();
        return alerts.findByCampIdIn(campIds).stream()
                .filter(alert -> alert.getType().routesToCampManager())
                .toList();
    }

    public Optional<Alert> visibleDetail(AppUser actor, Long alertId) {
        return alerts.findById(alertId).filter(alert -> isVisible(actor, alert));
    }

    public List<AlertTransition> transitionsFor(Long alertId) {
        return transitions.findByAlertIdOrderByAtTickAsc(alertId);
    }

    public List<Note> notesFor(Long alertId) {
        return notes.forSubject(NoteSubjectType.ALERT, alertId);
    }

    public boolean canAct(AppUser actor, Alert alert) {
        return hasCampScopedAccess(actor, alert);
    }

    private boolean isVisible(AppUser actor, Alert alert) {
        return hasCampScopedAccess(actor, alert);
    }

    private boolean hasCampScopedAccess(AppUser actor, Alert alert) {
        if (isOversight(actor)) {
            return true;
        }
        return actor.getRole() == Role.CAMP_MANAGER
                && alert.getType().routesToCampManager()
                && managesCamp(actor, alert.getCampId());
    }

    private boolean isOversight(AppUser actor) {
        return actor.getRole() == Role.COORDINATOR || actor.getRole() == Role.ADMIN;
    }

    private boolean managesCamp(AppUser actor, Long campId) {
        return assignments.existsByUserIdAndCampId(actor.getId(), campId);
    }

    private String demoDescription(AlertType type) {
        return switch (type) {
            case RESOURCE_SHORTAGE -> "DEMO: projected stock-out within the hour";
            case MEDICAL_EMERGENCY -> "DEMO: mass-casualty triage requested";
            case SECURITY_INCIDENT -> "DEMO: crowd-control support requested";
            case INFRASTRUCTURE_DAMAGE -> "DEMO: shelter structure reported unsafe";
        };
    }
}
