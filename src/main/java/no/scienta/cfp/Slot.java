package no.scienta.cfp;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/** The time slots, the same in every room. Fixed: nobody can add or change them (workflow/specs/scheduling.md). */
enum Slot {
    S1("09:00", "10:00"),
    S2("10:15", "11:15"),
    S3("11:30", "12:30"),
    S4("13:30", "15:30"),
    S5("15:45", "16:45");

    /** As on the signs: 24-hour clock, two-digit hours. */
    private static final DateTimeFormatter SIGN = DateTimeFormatter.ofPattern("HH:mm");

    private final LocalTime start;
    private final LocalTime end;

    Slot(String start, String end) {
        this.start = LocalTime.parse(start);
        this.end = LocalTime.parse(end);
    }

    String start() {
        return SIGN.format(start);
    }

    String end() {
        return SIGN.format(end);
    }

    /** The long slot, the only one a workshop fits in. */
    boolean isLong() {
        return this == S4;
    }

    /** The slot written exactly like this, or null if there is no such slot. */
    static Slot parse(String s) {
        return Arrays.stream(values()).filter(x -> x.name().equals(s)).findFirst().orElse(null);
    }
}
