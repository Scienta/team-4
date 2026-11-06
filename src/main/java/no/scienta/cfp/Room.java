package no.scienta.cfp;

import java.util.Arrays;

/** The venue's rooms. Fixed: nobody can add or change them (workflow/specs/scheduling.md). */
enum Room {
    R1, R2, R3;

    /** The room written exactly like this, or null if there is no such room. */
    static Room parse(String s) {
        return Arrays.stream(values()).filter(r -> r.name().equals(s)).findFirst().orElse(null);
    }
}
