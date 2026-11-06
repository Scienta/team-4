package no.scienta.cfp;

/**
 * A domain rule said no. The domain classes throw it without knowing about HTTP; {@link ApiErrors} turns the
 * {@link Kind} into a status code, and the message is what the client sees.
 */
final class Refused extends RuntimeException {

    enum Kind {
        /** The request itself is malformed or breaks a rule about its content. */
        INVALID,
        /** Something the request names doesn't exist. */
        NOT_FOUND,
        /** The caller may not do this to this thing. */
        NOT_PERMITTED,
        /** Not possible in the current state (status, limits, duplicates). */
        NOT_ALLOWED
    }

    final Kind kind;

    private Refused(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    static Refused invalid(String message) {
        return new Refused(Kind.INVALID, message);
    }

    static Refused notFound(String message) {
        return new Refused(Kind.NOT_FOUND, message);
    }

    static Refused notPermitted(String message) {
        return new Refused(Kind.NOT_PERMITTED, message);
    }

    static Refused notAllowed(String message) {
        return new Refused(Kind.NOT_ALLOWED, message);
    }
}
