package bd.dms.alert;

/** Published on every create, transition, or note against an alert; drives realtime push. */
public record AlertChangedEvent(Long alertId) {}
