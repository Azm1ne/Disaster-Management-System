package bd.dms.world;

/** What kind of thing a {@link GeometryHistory} row's geometry write belongs to. Camps have
 * point lat/lng only, not a polygon, so they never appear here. */
public enum GeometrySubjectType {
    DISASTER,
    AFFECTED_AREA
}
