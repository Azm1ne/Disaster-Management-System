package bd.dms.world;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeometryHistoryRepository extends JpaRepository<GeometryHistory, Long> {

    List<GeometryHistory> findBySubjectTypeAndSubjectIdOrderByCreatedAtAsc(
            GeometrySubjectType subjectType, Long subjectId);
}
