package bd.dms.world;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AffectedAreaRepository extends JpaRepository<AffectedArea, Long> {
    List<AffectedArea> findByDisasterId(Long disasterId);
}
