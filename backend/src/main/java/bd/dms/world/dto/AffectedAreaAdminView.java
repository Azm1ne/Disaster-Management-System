package bd.dms.world.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** An affected area as returned right after admin creation, with its geometry parsed to a
 * GeoJSON object for the client. */
public record AffectedAreaAdminView(Long id, Long disasterId, String nameEn, String nameBn, JsonNode geometry) {}
