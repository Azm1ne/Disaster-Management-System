package bd.dms.forecast;

import bd.dms.alert.AlertService;
import bd.dms.sim.WorldChangedEvent;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Recomputes every camp/resource's forecast on each simulated tick and raises a
 * {@code RESOURCE_SHORTAGE} alert when the pessimistic (worst-case) edge of the confidence band
 * says exhaustion is imminent. Listens to the same {@link WorldChangedEvent} as
 * {@code WorldBroadcaster} — no new scheduler, no transport dependency. {@code AlertService}'s
 * own dedupe (camp + type + resourceType, open alerts only) prevents re-raising every tick.
 */
@Component
public class ForecastAlertListener {

    /** Worst-case ticks remaining below which a shortage is imminent enough to alert on. */
    private static final long SHORTAGE_THRESHOLD_TICKS = 6;
    private static final List<String> RESOURCE_TYPES = List.of("WATER", "FOOD", "MEDICAL");

    private final CampRepository camps;
    private final ForecastService forecastService;
    private final AlertService alertService;

    public ForecastAlertListener(CampRepository camps, ForecastService forecastService, AlertService alertService) {
        this.camps = camps;
        this.forecastService = forecastService;
        this.alertService = alertService;
    }

    @EventListener
    public void onWorldChanged(WorldChangedEvent event) {
        if (!event.worldChanged()) {
            return;
        }
        long tick = event.clock().tick();
        for (Camp camp : camps.findAll()) {
            for (String resourceType : RESOURCE_TYPES) {
                ForecastResult forecast = forecastService.forecast(camp.getId(), resourceType, tick);
                if (forecast.ticksRemainingWorstCase() == null
                        || forecast.ticksRemainingWorstCase() > SHORTAGE_THRESHOLD_TICKS) {
                    continue;
                }
                alertService.raiseFromForecast(camp.getId(), resourceType, describe(forecast));
            }
        }
    }

    private String describe(ForecastResult forecast) {
        return String.format(
                "{\"resourceType\":\"%s\",\"ticksRemaining\":%d,\"confidence\":\"%s\"}",
                forecast.resourceType(), forecast.ticksRemainingEstimate(), forecast.confidenceLevel());
    }
}
