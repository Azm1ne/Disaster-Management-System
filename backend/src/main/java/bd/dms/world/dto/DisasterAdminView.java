package bd.dms.world.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** A disaster as the admin surface sees it — full headline facts plus its boundary geometry
 * (null until an admin draws one), parsed to a GeoJSON object for the client. */
public record DisasterAdminView(
        Long id, String code, String type, String status, String nameEn, String nameBn, JsonNode geometry) {}
