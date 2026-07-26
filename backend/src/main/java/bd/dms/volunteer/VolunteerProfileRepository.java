package bd.dms.volunteer;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VolunteerProfileRepository extends JpaRepository<VolunteerProfile, Long> {

    Optional<VolunteerProfile> findByUserId(Long userId);

    Optional<VolunteerProfile> findByCode(String code);
}
