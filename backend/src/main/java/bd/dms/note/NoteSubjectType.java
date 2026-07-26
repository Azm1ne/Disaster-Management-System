package bd.dms.note;

/** What kind of thing a {@link Note} is attached to. ALLOCATION covers both allocation decisions
 * and the cross-camp transfers they authorize — {@code AllocationDecision} already models a
 * transfer (source camp -&gt; target camp), so ticket 12 does not need a separate TRANSFER subject
 * type or entity. */
public enum NoteSubjectType {
    ALERT,
    ALLOCATION
}
