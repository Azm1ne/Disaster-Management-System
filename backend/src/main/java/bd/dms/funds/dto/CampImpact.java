package bd.dms.funds.dto;

import java.math.BigDecimal;

/** One camp's slice of a disaster's aggregate procurement — never traces back to an individual
 * donor's exact taka (money is fungible once it lands in the ledger), and never carries any
 * victim/family data: only camp identity, resource type, and aggregate amounts. */
public record CampImpact(
        Long campId,
        String campNameEn,
        String campNameBn,
        String resourceType,
        BigDecimal amountProcured,
        BigDecimal quantityProcured) {}
