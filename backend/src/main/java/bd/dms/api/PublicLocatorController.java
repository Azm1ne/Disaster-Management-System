package bd.dms.api;

import bd.dms.world.WorldService;
import bd.dms.world.dto.LocatorCamp;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public, no-login camp locator, so a displaced person can find shelter without an
 * account. It returns only name, location, and open/closed status across all disasters —
 * never capacity, population, or any operational field. {@code /public/**} is open in the
 * security config; the whitelist is enforced by the {@link LocatorCamp} shape itself.
 */
@RestController
@RequestMapping("/public")
public class PublicLocatorController {

    private final WorldService world;

    public PublicLocatorController(WorldService world) {
        this.world = world;
    }

    @GetMapping("/camps")
    public List<LocatorCamp> camps() {
        return world.publicLocator();
    }
}
