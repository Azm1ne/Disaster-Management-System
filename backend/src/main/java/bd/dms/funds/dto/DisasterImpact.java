package bd.dms.funds.dto;

import java.math.BigDecimal;
import java.util.List;

/** One disaster the donor has given to: their own total, plus the aggregated Donation → Camp
 * chain — the disaster's whole procurement fan-out, not a per-dollar trace. */
public record DisasterImpact(
        Long disasterId,
        String nameEn,
        String nameBn,
        BigDecimal donatedByMe,
        List<CampImpact> camps) {}
