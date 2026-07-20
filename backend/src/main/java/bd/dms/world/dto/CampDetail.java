package bd.dms.world.dto;

import java.util.List;

/** A single camp's full state, including the disaster it belongs to and its resource summary. */
public record CampDetail(
        Long id,
        String code,
        String nameEn,
        String nameBn,
        double lat,
        double lng,
        int capacity,
        int population,
        String status,
        DisasterRef disaster,
        List<ResourceView> resources) {

    /** The camp's parent disaster, named in both languages. */
    public record DisasterRef(Long id, String nameEn, String nameBn) {}
}
