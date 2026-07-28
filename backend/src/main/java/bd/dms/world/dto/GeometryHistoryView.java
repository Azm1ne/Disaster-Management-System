package bd.dms.world.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** One {@code GeometryHistory} row, geometries parsed to GeoJSON objects for the client.
 * {@code previousGeometry} is null for a subject's first write. */
public record GeometryHistoryView(
        Long id,
        Long subjectId,
        JsonNode previousGeometry,
        JsonNode newGeometry,
        Long actorUserId,
        Instant createdAt) {}
