package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;

import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import bd.dms.world.Camp;
import bd.dms.world.CampAssignmentRepository;
import bd.dms.world.CampRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The per-camp binding seam: after boot, the demo Camp Manager is bound to the camp they manage,
 * which is what the realtime layer authorizes per-camp topic subscriptions against.
 */
@SpringBootTest
class CampAssignmentSeederTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private CampRepository camps;

    @Autowired
    private CampAssignmentRepository assignments;

    @Test
    void demoCampManagerIsBoundToTheirCamp() {
        AppUser manager = users.findByUsername("camp_manager").orElseThrow();
        Camp camp = camps.findByCode("jam-kurigram-sadar").orElseThrow();

        assertThat(assignments.existsByUserIdAndCampId(manager.getId(), camp.getId())).isTrue();
    }

    @Test
    void managerIsNotBoundToACampTheyDoNotManage() {
        AppUser manager = users.findByUsername("camp_manager").orElseThrow();
        Camp otherCamp = camps.findByCode("jam-chilmari").orElseThrow();

        assertThat(assignments.existsByUserIdAndCampId(manager.getId(), otherCamp.getId())).isFalse();
    }
}
