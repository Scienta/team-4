package no.scienta.cfp;

import java.util.Arrays;

/**
 * The talk formats a speaker can submit, and how long each one is. The one place these live.
 *
 * <p>Declared longest first: {@code "format must be one of " + Arrays.toString(values())} is the API's error message.
 */
enum Format {
    WORKSHOP(120),
    PRESENTATION(45),
    LIGHTNING_20(20),
    LIGHTNING_10(10);
    // KEYNOTE(60): keynotes are invited, not submitted (2021)

    final int durationMinutes;

    Format(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    /** The format written exactly like this (capitals), or null if there is no such format. */
    static Format parse(String s) {
        return Arrays.stream(values()).filter(f -> f.name().equals(s)).findFirst().orElse(null);
    }
}
