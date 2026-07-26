package bd.dms.volunteer;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VolunteerTaskRepository extends JpaRepository<VolunteerTask, Long> {

    Optional<VolunteerTask> findByAlertId(Long alertId);

    List<VolunteerTask> findByStatus(VolunteerTaskStatus status);

    List<VolunteerTask> findByAssignedVolunteerId(Long volunteerId);
}
