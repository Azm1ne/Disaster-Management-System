package bd.dms.funds;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    List<Donation> findByDisasterId(Long disasterId);

    List<Donation> findByDonorUserIdOrderByCreatedAtDesc(Long donorUserId);
}
