package bd.dms.family.dto;

import java.util.List;

/**
 * A camp's trustworthy arriving-vs-arrived picture, per the dual-source confirmation rule:
 * {@code arrivingCount} is groups the representative has tapped but the manager has not yet
 * confirmed; {@code arrivedCount} is groups both sides have confirmed. Neither count touches
 * {@code Camp.population} — that stays the simulation engine's alone to write.
 */
public record CampArrivalsView(int arrivingCount, int arrivedCount, List<CampArrivalGroup> groups) {}
