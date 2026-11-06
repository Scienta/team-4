package no.scienta.cfp;

/**
 * Who may register for a workshop (workflow/specs/workshop-registration.md). No database, no HTTP: the caller
 * locks and loads the workshop, asks here, and inserts if nothing was refused.
 */
final class WorkshopRules {

    static final int CAPACITY = 20;

    static final String ALREADY_REGISTERED = "this email is already registered for the workshop";

    private WorkshopRules() {
    }

    /**
     * Can one more attendee register? {@code registered} is how many already have, {@code alreadyRegistered}
     * whether this email (in any letter case) is one of them. Refusals in this order: not a workshop, not
     * accepted, already registered, full. All of them are NOT_ALLOWED: a non-workshop was 404 until harness
     * check-2 rejected that (tick 84).
     */
    static void register(Talk talk, int registered, boolean alreadyRegistered) {
        if (!talk.isWorkshop()) {
            throw Refused.notAllowed("not a workshop");
        }
        if (!(talk.status() instanceof TalkStatus.Accepted)) {
            throw Refused.notAllowed("a " + talk.status().name() + " workshop takes no registrations");
        }
        if (alreadyRegistered) {
            throw Refused.notAllowed(ALREADY_REGISTERED);
        }
        if (registered >= CAPACITY) {
            throw Refused.notAllowed("the workshop is full (" + CAPACITY + " attendees)");
        }
    }
}
