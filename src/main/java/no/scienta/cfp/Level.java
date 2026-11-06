package no.scienta.cfp;

import java.util.Arrays;

/** A talk's level. */
enum Level {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED;

    /** The level written exactly like this (capitals), or null if there is no such level. */
    static Level parse(String s) {
        return Arrays.stream(values()).filter(l -> l.name().equals(s)).findFirst().orElse(null);
    }
}
