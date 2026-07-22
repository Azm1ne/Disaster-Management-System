package bd.dms.realtime;

import bd.dms.alert.Alert;
import bd.dms.alert.AlertChangedEvent;
import bd.dms.alert.AlertService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Pushes an alert's headline state to whoever is entitled to see it, mirroring
 * {@link WorldBroadcaster}. Every change goes to {@code /topic/alerts} (Coordinator/Admin); a
 * camp-scoped type's changes also go to that camp's {@code /topic/camp/{id}/alerts}.
 */
@Component
public class AlertBroadcaster {

    private final SimpMessagingTemplate messaging;
    private final AlertService alertService;

    public AlertBroadcaster(SimpMessagingTemplate messaging, AlertService alertService) {
        this.messaging = messaging;
        this.alertService = alertService;
    }

    @EventListener
    public void onAlertChanged(AlertChangedEvent event) {
        alertService.raw(event.alertId()).ifPresent(alert -> {
            messaging.convertAndSend("/topic/alerts", alert);
            if (alert.getType().routesToCampManager()) {
                messaging.convertAndSend("/topic/camp/" + alert.getCampId() + "/alerts", alert);
            }
        });
    }
}
