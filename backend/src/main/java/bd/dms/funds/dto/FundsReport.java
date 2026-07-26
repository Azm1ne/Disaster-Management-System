package bd.dms.funds.dto;

import java.math.BigDecimal;
import java.util.List;

/** The unaccounted-funds audit report: every disaster's ledger line, plus totals across all of
 * them. Coordinator/Admin only. */
public record FundsReport(
        List<DisasterFundSummary> disasters,
        BigDecimal totalDonated,
        BigDecimal totalProcured,
        BigDecimal totalUnaccounted) {}
