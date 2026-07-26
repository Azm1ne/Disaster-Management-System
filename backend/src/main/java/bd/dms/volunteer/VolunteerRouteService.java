package bd.dms.volunteer;

import bd.dms.volunteer.dto.RouteView;
import bd.dms.world.Camp;
import java.util.List;
import org.springframework.stereotype.Service;

/** Turns an OSRM lookup (or its absence) into the {@link RouteView} the client renders — a real
 * road polyline when OSRM answers, a straight two-point line when it doesn't. */
@Service
public class VolunteerRouteService {

    /** Assumed road speed (km/h) for the straight-line fallback's duration estimate — a plain
     * average, since there's no actual routed path to time. */
    private static final double FALLBACK_SPEED_KMH = 30.0;

    private final OsrmClient osrm;
    private final VolunteerScoringService scoring;

    public VolunteerRouteService(OsrmClient osrm, VolunteerScoringService scoring) {
        this.osrm = osrm;
        this.scoring = scoring;
    }

    public RouteView routeTo(VolunteerProfile volunteer, Camp destination) {
        return osrm.route(volunteer.getLat(), volunteer.getLng(), destination.getLat(), destination.getLng())
                .map(r -> new RouteView(r.pointsLatLng(), r.distanceMeters(), r.durationSeconds(), "OSRM"))
                .orElseGet(() -> straightLine(volunteer, destination));
    }

    private RouteView straightLine(VolunteerProfile volunteer, Camp destination) {
        double distanceKm = scoring.distanceKm(
                volunteer.getLat(), volunteer.getLng(), destination.getLat(), destination.getLng());
        double distanceMeters = distanceKm * 1000;
        double durationSeconds = (distanceKm / FALLBACK_SPEED_KMH) * 3600;
        List<double[]> points = List.of(
                new double[] {volunteer.getLat(), volunteer.getLng()},
                new double[] {destination.getLat(), destination.getLng()});
        return new RouteView(points, distanceMeters, durationSeconds, "STRAIGHT_LINE");
    }
}
