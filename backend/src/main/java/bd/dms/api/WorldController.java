package bd.dms.api;

import bd.dms.world.WorldService;
import bd.dms.world.dto.CampDetail;
import bd.dms.world.dto.DisasterView;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated world read API. Any signed-in role may read the shared picture — the
 * disasters with their geometry and camps, and a single camp's detail. Access for
 * unauthenticated callers is denied here (the public locator is a separate, narrower surface).
 */
@RestController
@RequestMapping("/world")
public class WorldController {

    private final WorldService world;

    public WorldController(WorldService world) {
        this.world = world;
    }

    @GetMapping("/disasters")
    public List<DisasterView> disasters() {
        return world.allDisasters();
    }

    @GetMapping("/camps/{id}")
    public ResponseEntity<CampDetail> camp(@PathVariable Long id) {
        return world.campDetail(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
