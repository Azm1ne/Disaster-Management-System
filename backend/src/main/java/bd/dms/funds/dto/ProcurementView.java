package bd.dms.funds.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProcurementView(
        Long id,
        Long disasterId,
        Long campId,
        String campNameEn,
        String campNameBn,
        String resourceType,
        BigDecimal amount,
        BigDecimal unitPrice,
        BigDecimal quantity,
        Instant createdAt) {}
