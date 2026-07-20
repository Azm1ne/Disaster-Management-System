package bd.dms.config;

import bd.dms.user.AppUser;
import bd.dms.user.Role;
import bd.dms.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds one demo user per role at startup. The demo has no self-registration, so these are
 * the only way in; the shared password is documented in the README. Idempotent: it inserts a
 * user only if the username is absent, so it is safe on every boot and in tests.
 *
 * <p>Hashing here with the real {@link PasswordEncoder} keeps bcrypt digests out of the SQL
 * migrations and lets the demo password be overridden per-environment.
 */
@Component
public class DemoUserSeeder implements CommandLineRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DemoUserSeeder(UserRepository users, PasswordEncoder passwordEncoder,
            @Value("${dms.demo.password:relief2026}") String demoPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(String... args) {
        seed("coordinator", Role.COORDINATOR, "Rehana Karim", "রেহানা করিম");
        seed("camp_manager", Role.CAMP_MANAGER, "Anwar Hossain", "আনোয়ার হোসেন");
        seed("donor", Role.DONOR, "Farhana Ahmed", "ফারহানা আহমেদ");
        seed("volunteer", Role.VOLUNTEER, "Sabbir Rahman", "সাব্বির রহমান");
        seed("victim", Role.VICTIM, "Jesmin Begum", "জেসমিন বেগম");
        seed("ngo", Role.NGO, "BRAC Relief Desk", "ব্র্যাক ত্রাণ ডেস্ক");
        seed("admin", Role.ADMIN, "System Administrator", "সিস্টেম অ্যাডমিনিস্ট্রেটর");
    }

    private void seed(String username, Role role, String nameEn, String nameBn) {
        if (users.findByUsername(username).isEmpty()) {
            users.save(new AppUser(username, passwordEncoder.encode(demoPassword), role, nameEn, nameBn));
        }
    }
}
