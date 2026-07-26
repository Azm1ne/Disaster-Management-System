package bd.dms.anomaly;

import bd.dms.user.AppUser;
import bd.dms.user.Role;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/review surface for {@link AnomalyFlag}s — Coordinator/Admin only, mirroring the oversight
 * gate {@code AllocationService} uses for its own queue. Detectors are the only writers of new
 * flags; this service only ever transitions an existing flag out of {@code OPEN} via {@link
 * AnomalyFlag#dispose}.
 */
@Service
public class AnomalyReviewService {

    private final AnomalyFlagRepository flags;

    public AnomalyReviewService(AnomalyFlagRepository flags) {
        this.flags = flags;
    }

    public List<AnomalyFlag> list(AppUser actor) {
        requireOversight(actor);
        return flags.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public AnomalyFlag review(AppUser actor, Long id, AnomalyFlagStatus toStatus, String note) {
        requireOversight(actor);
        AnomalyFlag flag = flags.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown anomaly flag: " + id));
        flag.dispose(toStatus, actor.getId(), note);
        return flag;
    }

    private void requireOversight(AppUser actor) {
        if (actor.getRole() != Role.COORDINATOR && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only Coordinator/Admin can review anomaly flags");
        }
    }
}
