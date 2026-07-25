package bd.dms.config;

import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import bd.dms.volunteer.Skill;
import bd.dms.volunteer.VolunteerProfile;
import bd.dms.volunteer.VolunteerProfileRepository;
import java.util.EnumSet;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a small roster of volunteer profiles around the Jamuna flood camps, so scoring has more
 * than one candidate to actually rank. Distinct from {@link AppUser} rows — mirrors family
 * members not being users (see {@code VolunteerProfile}'s class doc) — except the one entry that
 * IS the seeded "volunteer" login, which is what lets that account self-accept shifts and see its
 * own assignments. Deliberately leaves SECURITY uncovered by any roster volunteer, so the
 * skill-coverage gap panel always has a guaranteed, reproducible gap to show whenever a
 * SECURITY_INCIDENT alert (or its demo trigger) produces a task. Runs after {@link DemoUserSeeder}
 * (the "volunteer" user must exist first) and is idempotent, so it is safe on every boot and in
 * tests.
 */
@Component
@Order(3)
public class VolunteerSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final VolunteerProfileRepository volunteers;

    public VolunteerSeeder(UserRepository users, VolunteerProfileRepository volunteers) {
        this.users = users;
        this.volunteers = volunteers;
    }

    @Override
    public void run(String... args) {
        Long sabbirUserId = users.findByUsername("volunteer").map(AppUser::getId).orElse(null);

        seed("vol-sabbir", sabbirUserId, "Sabbir Rahman", "সাব্বির রহমান",
                25.810, 89.640, EnumSet.of(Skill.MEDICAL, Skill.LOGISTICS));
        seed("vol-nasrin", null, "Nasrin Akter", "নাসরিন আক্তার",
                25.556, 89.680, EnumSet.of(Skill.MEDICAL));
        seed("vol-kamal", null, "Kamal Uddin", "কামাল উদ্দিন",
                25.660, 89.625, EnumSet.of(Skill.LOGISTICS));
        seed("vol-farid", null, "Farid Molla", "ফরিদ মোল্লা",
                25.830, 89.555, EnumSet.of(Skill.ENGINEERING));
        seed("vol-ruma", null, "Ruma Khatun", "রুমা খাতুন",
                25.330, 89.535, EnumSet.of(Skill.MEDICAL, Skill.ENGINEERING));
        seed("vol-shanto", null, "Shanto Islam", "শান্ত ইসলাম",
                25.352, 89.678, EnumSet.of(Skill.LOGISTICS));
    }

    private void seed(String code, Long userId, String nameEn, String nameBn, double lat, double lng,
            EnumSet<Skill> skills) {
        Optional<VolunteerProfile> existing = volunteers.findByCode(code);
        if (existing.isEmpty()) {
            volunteers.save(new VolunteerProfile(code, userId, nameEn, nameBn, lat, lng, skills));
        }
    }
}
