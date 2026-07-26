package bd.dms.funds.dto;

import java.math.BigDecimal;
import java.util.List;

/** A donor's proof-of-impact view: every disaster they've given to, aggregated Donation → Camp,
 * with no victim data anywhere in the shape. */
public record DonorImpactView(List<DisasterImpact> disasters, BigDecimal totalDonatedByMe) {}
