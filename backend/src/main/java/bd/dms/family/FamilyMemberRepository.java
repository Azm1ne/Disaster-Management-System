package bd.dms.family;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    List<FamilyMember> findByFamilyGroupId(Long familyGroupId);

    int countByFamilyGroupId(Long familyGroupId);
}
