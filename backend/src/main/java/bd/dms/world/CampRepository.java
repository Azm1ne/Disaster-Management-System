package bd.dms.world;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampRepository extends JpaRepository<Camp, Long> {
    List<Camp> findByDisasterId(Long disasterId);

    List<Camp> findAllByOrderByNameEnAsc();
}
