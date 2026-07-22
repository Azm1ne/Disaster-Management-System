package bd.dms.family.dto;

/**
 * The reunification search whitelist: group name, camp, and status — the same discipline as
 * {@code LocatorCamp} for the public locator. No member roster, no medical data, ever.
 */
public record ReunificationResult(String groupName, String campNameEn, String campNameBn, ArrivalStatus status) {}
