package bd.dms.alert;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertTransitionRepository extends JpaRepository<AlertTransition, Long> {

    List<AlertTransition> findByAlertIdOrderByAtTickAsc(Long alertId);
}
