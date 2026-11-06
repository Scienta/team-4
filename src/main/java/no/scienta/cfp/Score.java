package no.scienta.cfp;

/** The whole number from 1 to 5 in a rating. */
record Score(int value) {

    /** The {@code score} of a rating request: a JSON whole number from 1 to 5. */
    static Score parse(Object raw) {
        if (!(raw instanceof Integer i)) {
            throw Refused.invalid("score must be a whole number");
        }
        if (i < 1 || i > 5) {
            throw Refused.invalid("score must be 1-5");
        }
        return new Score(i);
    }
}
