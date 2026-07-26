package bd.dms.volunteer;

/** How an ASSIGNED task got its volunteer: a coordinator push-assigned it, or the volunteer
 * self-accepted it from the open-shifts board. Null while a task is still OPEN. */
public enum AssignmentMethod {
    PUSH,
    SELF
}
