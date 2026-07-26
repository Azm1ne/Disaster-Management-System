package bd.dms.volunteer;

/** OPEN (unassigned, on the open-shifts board) -&gt; ASSIGNED (push-assigned or self-accepted) is
 * the only forward decision; a still-OPEN task is CANCELLED if its source alert closes/resolves
 * before anyone takes it. An ASSIGNED task is terminal in this slice — completion tracking is out
 * of scope, matching the ticket. */
public enum VolunteerTaskStatus {
    OPEN,
    ASSIGNED,
    CANCELLED
}
