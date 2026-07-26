package bd.dms.broadcast;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastReadRepository extends JpaRepository<BroadcastRead, BroadcastRead.Key> {

    List<BroadcastRead> findByBroadcastIdOrderByReadAtAsc(Long broadcastId);

    boolean existsByBroadcastIdAndUserId(Long broadcastId, Long userId);
}
