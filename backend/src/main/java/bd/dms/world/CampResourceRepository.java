package bd.dms.world;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampResourceRepository extends JpaRepository<CampResource, Long> {
    List<CampResource> findByCampId(Long campId);
}
