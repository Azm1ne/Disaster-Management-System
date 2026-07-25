package bd.dms.volunteer;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VolunteerTaskTransitionRepository extends JpaRepository<VolunteerTaskTransition, Long> {

    List<VolunteerTaskTransition> findByTaskIdOrderByAtTickAsc(Long taskId);
}
