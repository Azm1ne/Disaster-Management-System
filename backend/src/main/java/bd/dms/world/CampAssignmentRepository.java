package bd.dms.world;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampAssignmentRepository
        extends JpaRepository<CampAssignment, CampAssignment.Key> {

    boolean existsByUserIdAndCampId(Long userId, Long campId);

    List<CampAssignment> findByUserId(Long userId);
}
