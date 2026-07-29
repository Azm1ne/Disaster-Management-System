package bd.dms.proposal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisasterProposalRepository extends JpaRepository<DisasterProposal, Long> {

    List<DisasterProposal> findByStatus(ProposalStatus status);
}
