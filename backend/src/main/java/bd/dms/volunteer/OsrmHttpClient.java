package bd.dms.volunteer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The real OSRM lookup, against the public demo router by default (this is a showcase project,
 * not a production deployment — see {@code dms.osrm.base-url} to point at a self-hosted instance
 * instead). Deliberately swallows every failure mode (network error, timeout, non-200, malformed
 * body, "no route found") into {@code Optional.empty()} — routing is a nice-to-have overlay on an
 * assignment that already exists, never a reason to fail the request.
 */
@Component
public class OsrmHttpClient implements OsrmClient {

    private static final Logger log = LoggerFactory.getLogger(OsrmHttpClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;

    public OsrmHttpClient(@Value("${dms.osrm.base-url:https://router.project-osrm.org}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public Optional<RoutePolyline> route(double originLat, double originLng, double destLat, double destLng) {
        String url = String.format(
                Locale.ROOT, "%s/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson",
                baseUrl, originLng, originLat, destLng, destLat);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return parse(response.body());
        } catch (Exception e) {
            log.info("OSRM routing unavailable, falling back to straight line: {}", e.toString());
            return Optional.empty();
        }
    }

    private Optional<RoutePolyline> parse(String body) throws Exception {
        JsonNode root = mapper.readTree(body);
        if (!"Ok".equals(root.path("code").asText())) {
            return Optional.empty();
        }
        JsonNode route = root.path("routes").get(0);
        if (route == null) {
            return Optional.empty();
        }
        JsonNode coordinates = route.path("geometry").path("coordinates");
        List<double[]> points = new ArrayList<>();
        for (JsonNode coordinate : coordinates) {
            // GeoJSON is [lng, lat]; we store [lat, lng] to match every other lat/lng pair in
            // this codebase (Camp.lat/lng, VolunteerProfile.lat/lng).
            points.add(new double[] {coordinate.get(1).asDouble(), coordinate.get(0).asDouble()});
        }
        if (points.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RoutePolyline(points, route.path("distance").asDouble(), route.path("duration").asDouble()));
    }
}
