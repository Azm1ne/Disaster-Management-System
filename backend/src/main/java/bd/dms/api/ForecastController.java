package bd.dms.api;

import bd.dms.forecast.CampResourceObservation;
import bd.dms.forecast.CampResourceObservationRepository;
import bd.dms.forecast.ForecastAlertListener;
import bd.dms.forecast.ForecastResult;
import bd.dms.forecast.ForecastService;
import bd.dms.forecast.dto.ForecastView;
import bd.dms.sim.SimulationEngine;
import bd.dms.user.AppUser;
import bd.dms.user.Role;
import bd.dms.user.UserRepository;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import bd.dms.world.CampResource;
import bd.dms.world.CampResourceRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The explainable forecast read API (GET, added in Task 6) plus a DEMO-only trigger (POST) that
 * seeds a steep synthetic depletion and evaluates it through the same real forecast-then-alert
 * path {@code ForecastAlertListener} uses on every tick — the scripted Scenario structurally
 * never crosses the shortage threshold on its own (see Task 5b's rationale), so this is the only
 * way to demonstrate the pipeline live. */
@RestController
@RequestMapping("/forecasts")
public class ForecastController {

    private static final List<String> RESOURCE_TYPES = List.of("WATER", "FOOD", "MEDICAL");

    private final CampResourceRepository campResources;
    private final CampResourceObservationRepository observations;
    private final ForecastAlertListener forecastAlertListener;
    private final SimulationEngine engine;
    private final UserRepository users;
    private final CampRepository camps;
    private final ForecastService forecastService;

    public ForecastController(
            CampResourceRepository campResources,
            CampResourceObservationRepository observations,
            ForecastAlertListener forecastAlertListener,
            SimulationEngine engine,
            UserRepository users,
            CampRepository camps,
            ForecastService forecastService) {
        this.campResources = campResources;
        this.observations = observations;
        this.forecastAlertListener = forecastAlertListener;
        this.engine = engine;
        this.users = users;
        this.camps = camps;
        this.forecastService = forecastService;
    }

    @GetMapping
    public List<ForecastView> forecasts() {
        long tick = engine.currentTick();
        return camps.findAll().stream()
                .flatMap(camp -> RESOURCE_TYPES.stream().map(type -> toView(camp, type, tick)))
                .toList();
    }

    private ForecastView toView(Camp camp, String resourceType, long tick) {
        ForecastResult r = forecastService.forecast(camp.getId(), resourceType, tick);
        return new ForecastView(
                camp.getId(), camp.getCode(), camp.getNameEn(), camp.getNameBn(), resourceType,
                r.currentQuantity(), r.ratePerTick(), r.ticksRemainingEstimate(),
                r.ticksRemainingWorstCase(), r.ticksRemainingBestCase(), r.confidenceScore(),
                r.confidenceLevel(), r.latestObservedTick(), r.sampleCount());
    }

    @PostMapping("/demo/{campId}/{resourceType}")
    public ResponseEntity<Void> demo(
            @PathVariable Long campId, @PathVariable String resourceType, Authentication authentication) {
        AppUser actor = users.findByUsername(authentication.getName()).orElseThrow();
        if (actor.getRole() != Role.COORDINATOR && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only Coordinator/Admin can trigger a demo forecast");
        }
        BigDecimal startQuantity = campResources.findByCampId(campId).stream()
                .filter(r -> r.getResourceType().equals(resourceType))
                .map(CampResource::getQuantity)
                .findFirst()
                .orElse(BigDecimal.valueOf(600));

        // Anchor the 5-point depletion window so observedTick never goes negative: on a freshly
        // reset/early demo (currentTick() < 4) ForecastService's lookback floors at tick 0 and
        // would silently drop the earlier synthetic points, hiding the whole rate. Evaluating at
        // baseTick (>= the last seeded observation) keeps the forecast reading its own data as
        // "as of right now" regardless of where the real simulation clock happens to be.
        long baseTick = Math.max(engine.currentTick(), 4L);
        for (int i = 0; i <= 4; i++) {
            long observedTick = baseTick - 4 + i;
            BigDecimal quantity = startQuantity.multiply(BigDecimal.valueOf(1.0 - i * 0.2));
            observations.save(new CampResourceObservation(campId, resourceType, quantity, observedTick));
        }

        forecastAlertListener.evaluate(campId, resourceType, baseTick);
        return ResponseEntity.ok().build();
    }
}
