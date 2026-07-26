package bd.dms.volunteer;

import org.springframework.stereotype.Service;

/**
 * Pure scoring: skill x distance x urgency, exactly as the ticket spec words it (a product of
 * three [0,1] sub-scores, not a weighted sum like {@code AllocationScoringService}'s
 * priority score — a deliberate difference, since a volunteer with none of the needed skill
 * should rank at the very bottom regardless of how close or urgent the task is, which a weighted
 * sum would not guarantee).
 */
@Service
public class VolunteerScoringService {

    /** Distance (km) at which distanceScore has decayed to 0.5 — a volunteer next door scores
     * near 1.0, one 50km away scores near 0.17. */
    private static final double DISTANCE_DECAY_KM = 10.0;

    private static final double EARTH_RADIUS_KM = 6371.0;

    public double skillScore(VolunteerProfile volunteer, Skill required) {
        return volunteer.getSkills().contains(required) ? 1.0 : 0.0;
    }

    /** Haversine great-circle distance in kilometres. */
    public double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /** Monotonically decreasing in (0,1]: 1.0 at zero distance, decaying toward 0 as distance
     * grows, never actually reaching it (there is always some, if vanishing, chance a very far
     * volunteer is still the best available). */
    public double distanceScore(double distanceKm) {
        return DISTANCE_DECAY_KM / (DISTANCE_DECAY_KM + distanceKm);
    }

    public double score(double skillScore, double distanceScore, double urgencyScore) {
        return skillScore * distanceScore * urgencyScore;
    }
}
