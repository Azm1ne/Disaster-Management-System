package bd.dms.world.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** An affected area with its geometry already parsed to a GeoJSON object for the client. */
public record AffectedAreaView(Long id, String nameEn, String nameBn, JsonNode geometry) {}
