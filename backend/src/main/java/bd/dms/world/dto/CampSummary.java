package bd.dms.world.dto;

/** A camp's identity and headline state — everything a map marker needs, for authenticated eyes. */
public record CampSummary(
        Long id,
        String code,
        String nameEn,
        String nameBn,
        double lat,
        double lng,
        int capacity,
        int population,
        String status) {}
