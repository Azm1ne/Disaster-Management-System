package bd.dms.anomaly;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyFlagRepository extends JpaRepository<AnomalyFlag, Long> {

    List<AnomalyFlag> findAllByOrderByCreatedAtDesc();

    List<AnomalyFlag> findByDetectorTypeOrderByCreatedAtDesc(AnomalyDetectorType type);
}
