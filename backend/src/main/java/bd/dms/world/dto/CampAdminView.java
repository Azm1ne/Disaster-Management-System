package bd.dms.world.dto;

/** A camp as returned right after admin creation. */
public record CampAdminView(
        Long id,
        Long disasterId,
        String code,
        String nameEn,
        String nameBn,
        double lat,
        double lng,
        int capacity,
        int population,
        String status) {}
