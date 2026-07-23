package bd.dms.allocation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllocationDecisionRepository extends JpaRepository<AllocationDecision, Long> {

    List<AllocationDecision> findByTargetCampIdIn(List<Long> campIds);

    Optional<AllocationDecision> findBySourceCampIdAndTargetCampIdAndResourceTypeAndStatus(
            Long sourceCampId, Long targetCampId, String resourceType, AllocationStatus status);

    List<AllocationDecision> findBySourceCampIdAndResourceTypeAndStatusIn(
            Long sourceCampId, String resourceType, List<AllocationStatus> statuses);

    List<AllocationDecision> findByTargetCampIdAndResourceTypeAndStatusIn(
            Long targetCampId, String resourceType, List<AllocationStatus> statuses);
}
