package bd.dms.world.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** A whole disaster world: its headline facts, the areas to draw, and the camps inside it.
 * {@code geometry} is the admin-drawn boundary polygon (null until an admin draws one — the two
 * ticket-3 seeded demo disasters predate manual registration and have none), parsed to a GeoJSON
 * object for the client, matching {@link AffectedAreaView#geometry}'s convention. */
public record DisasterView(
        Long id,
        String code,
        String type,
        String status,
        String nameEn,
        String nameBn,
        JsonNode geometry,
        List<AffectedAreaView> affectedAreas,
        List<CampSummary> camps) {}
