package bd.dms.api;

import bd.dms.family.FamilyService;
import bd.dms.family.dto.CampArrivalGroup;
import bd.dms.family.dto.CampArrivalsView;
import bd.dms.family.dto.FamilyGroupStatus;
import bd.dms.family.dto.MedicalFlagRequest;
import bd.dms.family.dto.RegisterGroupRequest;
import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Victim/family registration, dual-source arrival, and the staff side of it for a camp's own
 * manager. {@code /family/**} is the victim's own group (see {@link bd.dms.security.SecurityConfig});
 * {@code /camp/{id}/arrivals/**} is the camp-scoped staff surface, gated the same way the
 * realtime camp topic is: Coordinator/Admin see any camp, a Camp Manager only a camp they are
 * assigned to (enforced in {@link FamilyService}).
 */
@RestController
public class FamilyController {

    private final FamilyService family;
    private final UserRepository users;

    public FamilyController(FamilyService family, UserRepository users) {
        this.family = family;
        this.users = users;
    }

    @PostMapping("/family/register")
    public FamilyGroupStatus register(Authentication authentication, @Valid @RequestBody RegisterGroupRequest request) {
        return family.registerGroup(callerId(authentication), request);
    }

    @GetMapping("/family/me")
    public ResponseEntity<FamilyGroupStatus> myGroup(Authentication authentication) {
        return family.myGroup(callerId(authentication))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/family/me/arrived")
    public FamilyGroupStatus confirmArrival(Authentication authentication) {
        return family.confirmRepresentativeArrival(callerId(authentication));
    }

    @GetMapping("/camp/{campId}/arrivals")
    public CampArrivalsView campArrivals(Authentication authentication, @PathVariable Long campId) {
        AppUser caller = caller(authentication);
        return family.campArrivals(caller.getId(), caller.getRole(), campId);
    }

    @PostMapping("/camp/{campId}/arrivals/{groupId}/confirm")
    public CampArrivalGroup confirmManagerArrival(
            Authentication authentication, @PathVariable Long campId, @PathVariable Long groupId) {
        AppUser caller = caller(authentication);
        return family.confirmManagerArrival(caller.getId(), caller.getRole(), campId, groupId);
    }

    @PatchMapping("/camp/{campId}/arrivals/{groupId}/members/{memberId}/medical-flag")
    public ResponseEntity<Void> setMedicalFlag(
            Authentication authentication,
            @PathVariable Long campId,
            @PathVariable Long groupId,
            @PathVariable Long memberId,
            @RequestBody MedicalFlagRequest request) {
        AppUser caller = caller(authentication);
        family.setMedicalFlag(caller.getId(), caller.getRole(), campId, groupId, memberId, request.medicalFlag());
        return ResponseEntity.noContent().build();
    }

    private Long callerId(Authentication authentication) {
        return caller(authentication).getId();
    }

    private AppUser caller(Authentication authentication) {
        return users.findByUsername(authentication.getName()).orElseThrow();
    }
}
