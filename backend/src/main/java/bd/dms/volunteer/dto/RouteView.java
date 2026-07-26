package bd.dms.volunteer.dto;

import java.util.List;

/** {@code source} is {@code "OSRM"} for a real road route or {@code "STRAIGHT_LINE"} for the
 * two-point fallback used whenever OSRM is unavailable. */
public record RouteView(
        List<double[]> points, double distanceMeters, double durationSeconds, String source) {}
