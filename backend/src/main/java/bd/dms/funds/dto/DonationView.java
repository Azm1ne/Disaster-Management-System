package bd.dms.funds.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record DonationView(
        Long id,
        Long disasterId,
        String disasterNameEn,
        String disasterNameBn,
        BigDecimal amount,
        Instant createdAt) {}
