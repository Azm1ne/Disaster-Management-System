package bd.dms.dm;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    @Query("select m from DirectMessage m "
            + "where (m.senderUserId = :a and m.recipientUserId = :b) "
            + "   or (m.senderUserId = :b and m.recipientUserId = :a) "
            + "order by m.createdAt asc")
    List<DirectMessage> findThread(@Param("a") Long userA, @Param("b") Long userB);
}
