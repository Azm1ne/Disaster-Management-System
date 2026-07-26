package bd.dms.broadcast;

import bd.dms.user.Role;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {

    List<Broadcast> findByTargetRoleOrderByCreatedAtDesc(Role targetRole);

    List<Broadcast> findAllByOrderByCreatedAtDesc();
}
