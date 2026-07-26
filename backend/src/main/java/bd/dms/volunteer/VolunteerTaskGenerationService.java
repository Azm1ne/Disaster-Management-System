package bd.dms.volunteer;

import bd.dms.alert.Alert;
import bd.dms.alert.AlertChangedEvent;
import bd.dms.alert.AlertRepository;
import bd.dms.alert.AlertSla;
import bd.dms.alert.AlertStatus;
import bd.dms.sim.SimulationEngine;
import java.util.List;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the volunteer task queue in lockstep with the alert lifecycle: every open alert gets
 * exactly one OPEN {@link VolunteerTask} for the skill {@link Skill#forAlertType(bd.dms.alert.AlertType)}
 * maps to, refreshed in place as the alert's urgency changes; a still-OPEN task is cancelled if
 * its alert resolves/closes before anyone took it. Never touches an already-ASSIGNED task — once
 * a volunteer is dispatched, closing the alert does not undo that.
 *
 * <p>This reuses the alert lifecycle's own two demo entry points (the "raise alert" form and the
 * existing {@code POST /alerts/demo} trigger) instead of adding a volunteer-specific one: any
 * alert a coordinator raises, live or demo, deterministically produces exactly one assignable
 * task the moment this listener fires — so the acceptance criteria's "a live demo run produces a
 * volunteer-assignable need" holds without new endpoint surface.
 */
@Component
public class VolunteerTaskGenerationService {

    private static final List<AlertStatus> CLOSED_STATUSES = List.of(AlertStatus.RESOLVED, AlertStatus.CLOSED);

    private final AlertRepository alerts;
    private final VolunteerTaskRepository tasks;
    private final VolunteerTaskTransitionRepository transitions;
    private final SimulationEngine engine;

    public VolunteerTaskGenerationService(
            AlertRepository alerts,
            VolunteerTaskRepository tasks,
            VolunteerTaskTransitionRepository transitions,
            SimulationEngine engine) {
        this.alerts = alerts;
        this.tasks = tasks;
        this.transitions = transitions;
        this.engine = engine;
    }

    @EventListener
    @Transactional
    public void onAlertChanged(AlertChangedEvent event) {
        alerts.findById(event.alertId()).ifPresent(this::sync);
    }

    private void sync(Alert alert) {
        Optional<VolunteerTask> existing = tasks.findByAlertId(alert.getId());
        long tick = engine.currentTick();

        if (CLOSED_STATUSES.contains(alert.getStatus())) {
            existing.filter(task -> task.getStatus() == VolunteerTaskStatus.OPEN)
                    .ifPresent(task -> cancel(task, tick));
            return;
        }

        double urgency = urgencyScore(alert, tick);
        if (existing.isPresent()) {
            existing.get().refreshUrgency(urgency);
            return;
        }
        Skill skill = Skill.forAlertType(alert.getType());
        tasks.save(new VolunteerTask(
                alert.getId(), alert.getCampId(), skill, taskDescription(skill), urgency, tick));
    }

    private void cancel(VolunteerTask task, long tick) {
        VolunteerTaskStatus from = task.getStatus();
        task.cancel();
        transitions.save(new VolunteerTaskTransition(
                task.getId(), from, VolunteerTaskStatus.CANCELLED, null, "Source alert closed", tick));
    }

    /** 1.0 once the SLA has already breached (ESCALATED, or past its deadline), rising linearly
     * from 0.0 as the deadline approaches — mirrors {@code AllocationScoringService.shortageScore}'s
     * shape. */
    private double urgencyScore(Alert alert, long tick) {
        if (alert.getStatus() == AlertStatus.ESCALATED) {
            return 1.0;
        }
        long thresholdTicks = AlertSla.thresholdTicks(alert.getType());
        long ticksRemaining = alert.getSlaDeadlineTick() - tick;
        double raw = 1.0 - (double) ticksRemaining / thresholdTicks;
        return Math.max(0.0, Math.min(1.0, raw));
    }

    private String taskDescription(Skill skill) {
        return switch (skill) {
            case MEDICAL -> "Medical support volunteers needed";
            case LOGISTICS -> "Distribution/logistics volunteers needed";
            case SECURITY -> "Crowd-control/security volunteers needed";
            case ENGINEERING -> "Structural/engineering support volunteers needed";
        };
    }
}
