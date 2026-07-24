package bd.dms.family;

import static org.assertj.core.api.Assertions.assertThat;

import bd.dms.user.AppUser;
import bd.dms.user.Role;
import bd.dms.user.UserRepository;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * family_groups.owner_user_id and .camp_id are both FK-constrained (V8__family.sql), so test
 * fixtures need real seeded users/camps rather than arbitrary ids — the two camps used here are
 * whichever two the demo world seed already provides.
 */
@SpringBootTest
class FamilyMemberRepositoryTest {

    @Autowired
    private FamilyGroupRepository groups;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private UserRepository users;

    @Autowired
    private CampRepository camps;

    @Test
    void countsMembersAndMedicalFlaggedMembersForACamp() {
        List<Camp> allCamps = camps.findAllByOrderByNameEnAsc();
        long campId = allCamps.get(0).getId();
        long otherCampId = allCamps.get(1).getId();

        // The shared demo seed may already have registered families at these camps (other
        // integration tests run against the same database), so assert deltas, not absolutes.
        long baselineCampCount = members.countByCampId(campId);
        long baselineCampMedicalCount = members.countMedicalFlagTrueByCampId(campId);
        long baselineOtherCampCount = members.countByCampId(otherCampId);

        AppUser ownerA = users.save(new AppUser("test-owner-a", "hash", Role.VICTIM, "Owner A", "Owner A"));
        AppUser ownerB = users.save(new AppUser("test-owner-b", "hash", Role.VICTIM, "Owner B", "Owner B"));
        AppUser ownerC = users.save(new AppUser("test-owner-c", "hash", Role.VICTIM, "Owner C", "Owner C"));

        FamilyGroup groupA = groups.save(new FamilyGroup(ownerA.getId(), campId, "Test group A"));
        FamilyGroup groupB = groups.save(new FamilyGroup(ownerB.getId(), campId, "Test group B"));
        FamilyGroup groupElsewhere =
                groups.save(new FamilyGroup(ownerC.getId(), otherCampId, "Test group C"));

        FamilyMember flagged = new FamilyMember(groupA.getId(), "Nickname A1", "ADULT");
        flagged.setMedicalFlag(true);
        members.save(flagged);
        members.save(new FamilyMember(groupA.getId(), "Nickname A2", "CHILD"));
        members.save(new FamilyMember(groupB.getId(), "Nickname B1", "ADULT"));
        members.save(new FamilyMember(groupElsewhere.getId(), "Nickname C1", "ADULT"));

        assertThat(members.countByCampId(campId)).isEqualTo(baselineCampCount + 3L);
        assertThat(members.countMedicalFlagTrueByCampId(campId)).isEqualTo(baselineCampMedicalCount + 1L);
        assertThat(members.countByCampId(otherCampId)).isEqualTo(baselineOtherCampCount + 1L);
        assertThat(members.countMedicalFlagTrueByCampId(-1L)).isEqualTo(0L);
    }
}
