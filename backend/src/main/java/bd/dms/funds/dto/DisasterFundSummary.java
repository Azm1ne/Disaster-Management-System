package bd.dms.funds.dto;

import java.math.BigDecimal;

/** One disaster's line in the unaccounted-funds report: money in, money spent via procurement,
 * and the gap between them (funds donated but not yet turned into resources). */
public record DisasterFundSummary(
        Long disasterId,
        String nameEn,
        String nameBn,
        BigDecimal donated,
        BigDecimal procured,
        BigDecimal unaccounted) {}
