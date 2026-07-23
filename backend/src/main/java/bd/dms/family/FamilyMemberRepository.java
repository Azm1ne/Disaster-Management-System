package bd.dms.family;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    List<FamilyMember> findByFamilyGroupId(Long familyGroupId);

    int countByFamilyGroupId(Long familyGroupId);

    @Query("select count(m) from FamilyMember m where m.familyGroupId in "
            + "(select g.id from FamilyGroup g where g.campId = :campId)")
    long countByCampId(@Param("campId") Long campId);

    @Query("select count(m) from FamilyMember m where m.medicalFlag = true and m.familyGroupId in "
            + "(select g.id from FamilyGroup g where g.campId = :campId)")
    long countMedicalFlagTrueByCampId(@Param("campId") Long campId);
}
