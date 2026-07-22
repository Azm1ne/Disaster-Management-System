package bd.dms.api;

import bd.dms.family.FamilyService;
import bd.dms.family.dto.ReunificationResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reunification: search, not browse. Anyone — no login — may look for a registered group, but
 * only by query, and only ever gets back {@link ReunificationResult}'s narrow shape (group
 * name, camp, status). No roster, no medical data, ever. {@code /public/**} is permitAll in
 * {@link bd.dms.security.SecurityConfig}.
 */
@RestController
@RequestMapping("/public")
public class PublicFamilySearchController {

    private final FamilyService family;

    public PublicFamilySearchController(FamilyService family) {
        this.family = family;
    }

    @GetMapping("/family-search")
    public List<ReunificationResult> search(@RequestParam(name = "q", defaultValue = "") String query) {
        return family.search(query);
    }
}
