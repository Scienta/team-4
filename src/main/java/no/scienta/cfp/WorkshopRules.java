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

    /** Why a registration was refused. The caller maps it to a response. */
    enum Reason { NOT_A_WORKSHOP, NOT_OPEN, DUPLICATE, FULL }

    static final class Refused extends RuntimeException {
        final Reason reason;

        Refused(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    /** Is this talk a workshop at all? Registrations can only be listed for workshops. */
    static void requireWorkshop(String format) {
        if (!WORKSHOP.equals(format)) {
            throw new Refused(Reason.NOT_A_WORKSHOP, "not a workshop");
        }
    }

    /**
     * Can one more attendee register? {@code registered} is how many already have, {@code alreadyRegistered}
     * whether this email (in any letter case) is one of them. Refusals in this order: not a workshop, not
     * accepted, already registered, full.
     */
    static void register(String format, TalkStatus status, int registered, boolean alreadyRegistered) {
        requireWorkshop(format);
        if (!(status instanceof TalkStatus.Accepted)) {
            throw new Refused(Reason.NOT_OPEN, "a " + status.name() + " workshop takes no registrations");
        }
        if (alreadyRegistered) {
            throw new Refused(Reason.DUPLICATE, "this email is already registered for the workshop");
        }
        if (registered >= CAPACITY) {
            throw new Refused(Reason.FULL, "the workshop is full (" + CAPACITY + " attendees)");
        }
    }
}
