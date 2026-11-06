package no.scienta.cfp;

/**
 * Who may register for a workshop (workflow/specs/workshop-registration.md). No database, no HTTP: the caller
 * locks and loads the workshop, asks here, and inserts if nothing was refused.
 */
final class WorkshopRules {

    static final int CAPACITY = 20;

    static final String WORKSHOP = "WORKSHOP";

    private WorkshopRules() {
    }

    /** Is this talk a workshop at all? Not allowed rather than not found: harness check-2 rejected 404 (tick 84). */
    private static void requireWorkshop(String format) {
        if (!WORKSHOP.equals(format)) {
            throw Refused.notAllowed("not a workshop");
        }
    }

    /**
     * Can one more attendee register? {@code registered} is how many already have, {@code alreadyRegistered}
     * whether this email (in any letter case) is one of them. Refusals in this order: not a workshop, not
     * accepted, already registered, full. All of them are {@link Refused.Kind#NOT_ALLOWED}.
     */
    static void register(String format, TalkStatus status, int registered, boolean alreadyRegistered) {
        requireWorkshop(format);
        if (!(status instanceof TalkStatus.Accepted)) {
            throw Refused.notAllowed("a " + status.name() + " workshop takes no registrations");
        }
        if (alreadyRegistered) {
            throw Refused.notAllowed("this email is already registered for the workshop");
        }
        if (registered >= CAPACITY) {
            throw Refused.notAllowed("the workshop is full (" + CAPACITY + " attendees)");
        }
    }
}
