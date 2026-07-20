package bd.dms.world.dto;

/**
 * The public locator's view of a camp: name, location, and open/closed status only. This
 * record is the whitelist — capacity, population, and resources are deliberately absent so
 * they cannot leak to unauthenticated callers.
 */
public record LocatorCamp(Long id, String nameEn, String nameBn, double lat, double lng, String status) {}
