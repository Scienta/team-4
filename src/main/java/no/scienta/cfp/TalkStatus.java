package no.scienta.cfp;

/**
 * A talk's status as a state machine (workflow/specs/acceptance.md). No database, no HTTP.
 *
 * <p>Each state only has the transitions it allows, so an illegal move doesn't compile: {@link Waitlisted} has no
 * {@code waitlist()}, {@link Rejected} has no {@code withdraw()}. Requests arrive as data, though, so
 * {@link #decide} and {@link #withdraw} switch over every state (exhaustively: a new state won't compile until it
 * is handled) and refuse what the state doesn't offer.
 *
 * <pre>
 *   SUBMITTED ──waitlist──▶ WAITLISTED
 *   SUBMITTED, WAITLISTED ──accept (≥2 reviewers)──▶ ACCEPTED
 *   SUBMITTED, WAITLISTED ──reject──▶ REJECTED
 *   SUBMITTED, WAITLISTED, ACCEPTED ──withdraw (main speaker)──▶ WITHDRAWN
 * </pre>
 */
sealed interface TalkStatus {

    int MIN_REVIEWERS_TO_ACCEPT = 2;

    /** As stored in {@code talk.status} and shown in the API. */
    String name();

    /**
     * Does a talk in this state count towards its speakers' cap ({@link Submission#MAX_TALKS_PER_SPEAKER})? Rejected
     * and withdrawn talks don't (change request: submission cap). {@code TalkDao.LIVE_TALKS_OF_SPEAKER} is the same
     * rule in SQL.
     */
    default boolean countsTowardsCap() {
        return true;
    }

    // ------------------------------------------------------------ capabilities

    /** The committee can still decide. */
    sealed interface Undecided extends TalkStatus {
        default Accepted accept(int reviewers) {
            if (reviewers < MIN_REVIEWERS_TO_ACCEPT) {
                throw Refused.notAllowed(
                        "a talk needs ratings from at least " + MIN_REVIEWERS_TO_ACCEPT + " reviewers to be accepted");
            }
            return new Accepted();
        }

        default Rejected reject() {
            return new Rejected();
        }
    }

    /** The speaker can still withdraw. */
    sealed interface Withdrawable extends TalkStatus {
        default Withdrawn withdraw() {
            return new Withdrawn();
        }
    }

    // ------------------------------------------------------------ states

    record Submitted() implements Undecided, Withdrawable {
        Waitlisted waitlist() {
            return new Waitlisted();
        }

        public String name() {
            return "SUBMITTED";
        }
    }

    record Waitlisted() implements Undecided, Withdrawable {
        public String name() {
            return "WAITLISTED";
        }
    }

    record Accepted() implements Withdrawable {
        public String name() {
            return "ACCEPTED";
        }
    }

    record Rejected() implements TalkStatus {
        public String name() {
            return "REJECTED";
        }

        public boolean countsTowardsCap() {
            return false;
        }
    }

    record Withdrawn() implements TalkStatus {
        public String name() {
            return "WITHDRAWN";
        }

        public boolean countsTowardsCap() {
            return false;
        }
    }

    /** A stored status. Anything else in the database is a bug, not a client error. */
    static TalkStatus of(String name) {
        return switch (name) {
            case "SUBMITTED" -> new Submitted();
            case "WAITLISTED" -> new Waitlisted();
            case "ACCEPTED" -> new Accepted();
            case "REJECTED" -> new Rejected();
            case "WITHDRAWN" -> new Withdrawn();
            default -> throw new IllegalStateException("unknown talk status " + name);
        };
    }

    // ------------------------------------------------------------ requests

    /** What the committee can ask for in {@code PUT /talks/{id}/status}. */
    enum Decision {
        ACCEPTED, REJECTED, WAITLISTED;

        /** The request's {@code status} value; not a string or not a decision: INVALID. */
        static Decision parse(Object status) {
            for (Decision d : values()) {
                if (d.name().equals(status)) {
                    return d;
                }
            }
            throw Refused.invalid("status must be one of ACCEPTED, REJECTED, WAITLISTED");
        }
    }

    /** The committee's decision; {@code reviewers} is how many different reviewers rated the talk. */
    default TalkStatus decide(Decision decision, int reviewers) {
        return switch (this) {
            case Submitted s -> switch (decision) {
                case ACCEPTED -> s.accept(reviewers);
                case REJECTED -> s.reject();
                case WAITLISTED -> s.waitlist();
            };
            case Waitlisted w -> switch (decision) {
                case ACCEPTED -> w.accept(reviewers);
                case REJECTED -> w.reject();
                case WAITLISTED -> throw notAllowed("become " + decision);
            };
            case Accepted a -> throw notAllowed("become " + decision);
            case Rejected r -> throw notAllowed("become " + decision);
            case Withdrawn x -> throw notAllowed("become " + decision);
        };
    }

    /** Speaker {@code requester} withdraws a talk whose main speaker is {@code owner}. Nobody else may, in any state. */
    default Withdrawn withdraw(long owner, long requester) {
        if (owner != requester) {
            throw Refused.notPermitted("only the talk's speaker can withdraw it");
        }
        return switch (this) {
            case Withdrawable w -> w.withdraw();
            case Rejected r -> throw notAllowed("be withdrawn");
            case Withdrawn x -> throw notAllowed("be withdrawn");
        };
    }

    private Refused notAllowed(String what) {
        return Refused.notAllowed("a " + name() + " talk can't " + what);
    }
}
