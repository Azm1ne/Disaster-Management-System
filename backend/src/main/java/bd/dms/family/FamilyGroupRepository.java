package bd.dms.family;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyGroupRepository extends JpaRepository<FamilyGroup, Long> {

    Optional<FamilyGroup> findByOwnerUserId(Long ownerUserId);

    List<FamilyGroup> findByCampId(Long campId);

    List<FamilyGroup> findByGroupNameContainingIgnoreCase(String groupName);
}
