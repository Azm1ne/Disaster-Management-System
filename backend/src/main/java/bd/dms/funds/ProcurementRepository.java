package bd.dms.funds;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcurementRepository extends JpaRepository<Procurement, Long> {

    List<Procurement> findByDisasterId(Long disasterId);
}
