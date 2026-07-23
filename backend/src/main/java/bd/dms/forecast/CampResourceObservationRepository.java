package bd.dms.forecast;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampResourceObservationRepository extends JpaRepository<CampResourceObservation, Long> {

    List<CampResourceObservation> findByCampIdAndResourceTypeAndTickGreaterThanEqualOrderByTickAsc(
            Long campId, String resourceType, long fromTick);

    void deleteByCampIdAndResourceTypeAndTickIn(Long campId, String resourceType, List<Long> ticks);
}
