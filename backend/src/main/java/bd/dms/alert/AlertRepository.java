package bd.dms.alert;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByCampIdIn(List<Long> campIds);

    List<Alert> findByStatusIn(List<AlertStatus> statuses);
}
