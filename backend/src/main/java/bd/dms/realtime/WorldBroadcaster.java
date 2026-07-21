package bd.dms.realtime;

import bd.dms.sim.WorldChangedEvent;
import bd.dms.world.WorldService;
import bd.dms.world.dto.DisasterView;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Pushes simulated change to whoever is entitled to see it. Listens for the engine's
 * {@link WorldChangedEvent} and sends the clock to {@code /topic/simulation}, the whole world to
 * {@code /topic/world}, and each camp's detail to its own {@code /topic/camp/{id}} — the
 * per-camp topics being what lets a Camp Manager receive their camp and nothing else.
 *
 * <p>A clock-only change (pause, resume, speed) skips the world read entirely.
 */
@Component
public class WorldBroadcaster {

    private final SimpMessagingTemplate messaging;
    private final WorldService worldService;

    public WorldBroadcaster(SimpMessagingTemplate messaging, WorldService worldService) {
        this.messaging = messaging;
        this.worldService = worldService;
    }

    @EventListener
    public void onWorldChanged(WorldChangedEvent event) {
        messaging.convertAndSend("/topic/simulation", event.clock());
        if (!event.worldChanged()) {
            return;
        }
        List<DisasterView> world = worldService.allDisasters();
        messaging.convertAndSend("/topic/world", world);
        for (DisasterView disaster : world) {
            disaster.camps().forEach(camp -> worldService
                    .campDetail(camp.id())
                    .ifPresent(detail -> messaging.convertAndSend("/topic/camp/" + camp.id(), detail)));
        }
    }
}
