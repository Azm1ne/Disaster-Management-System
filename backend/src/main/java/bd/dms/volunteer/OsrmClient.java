package bd.dms.volunteer;

import java.util.List;
import java.util.Optional;

/** A road route between two points. {@link #route} returns empty whenever OSRM is unreachable,
 * slow, or returns something unusable — the caller (see {@code VolunteerRouteService}) falls back
 * to a straight line, never propagating an OSRM failure as an error to the client. */
public interface OsrmClient {

    record RoutePolyline(List<double[]> pointsLatLng, double distanceMeters, double durationSeconds) {}

    Optional<RoutePolyline> route(double originLat, double originLng, double destLat, double destLng);
}
