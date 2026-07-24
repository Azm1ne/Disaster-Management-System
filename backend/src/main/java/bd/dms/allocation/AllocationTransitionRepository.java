package bd.dms.allocation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllocationTransitionRepository extends JpaRepository<AllocationTransition, Long> {

    List<AllocationTransition> findByAllocationIdOrderByAtTickAsc(Long allocationId);
}
