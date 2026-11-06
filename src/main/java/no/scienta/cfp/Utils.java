package no.scienta.cfp;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Misc helpers.
 */
public class Utils {

    public static final String DB_DATE_TIME = "yyyy-MM-dd HH:mm:ss";

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /**
     * Today's UTC date as dd.MM.yyyy (speaker.created). UTC like {@link #now()}, not the JVM's zone.
     */
    public static String today() {
        SimpleDateFormat f = new SimpleDateFormat("dd.MM.yyyy");
        f.setTimeZone(UTC);
        return f.format(new Date());
    }

    public static String now() {
        SimpleDateFormat f = new SimpleDateFormat(DB_DATE_TIME);
        f.setTimeZone(UTC);
        return f.format(new Date());
    }

    /**
     * Parses a {@link #DB_DATE_TIME} string in UTC. Strict: "2026-13-45" is an error, not a date in 2027.
     *
     * @throws IllegalArgumentException if {@code s} isn't a valid date-time
     */
    public static Date parse(String s) {
        SimpleDateFormat f = new SimpleDateFormat(DB_DATE_TIME);
        f.setTimeZone(UTC);
        f.setLenient(false);
        try {
            return f.parse(s);
        } catch (ParseException e) {
            throw new IllegalArgumentException("not a " + DB_DATE_TIME + " date-time: " + s, e);
        }
    }

    /**
     * Epoch millis to a date-only java.sql.Date (UTC day).
     */
    public static java.sql.Date toSqlDate(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
        f.setTimeZone(UTC);
        return java.sql.Date.valueOf(f.format(new Date(millis)));
    }

    /**
     * "11/4/26, 1:05 PM", what the reviewer UI shows.
     */
    public static String usShort(Date d) {
        // Spelled out instead of DateFormat.SHORT: since JDK 20 (CLDR 42) that puts U+202F before "PM".
        SimpleDateFormat f = new SimpleDateFormat("M/d/yy, h:mm a", Locale.US);
        f.setTimeZone(UTC);
        return f.format(d);
    }

    /**
     * "4. nov. 2026" for the committee spreadsheet.
     */
    public static String norwegianDate(Date d) {
        DateFormat f = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.of("nb", "NO"));
        f.setTimeZone(TimeZone.getTimeZone("Europe/Oslo"));
        return f.format(d);
    }

    /**
     * Makes a value safe for the semicolon CSV export.
     */
    public static String csv(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(";") || s.contains("\"")) {
            return "\"" + s.replace("\"", "'") + "\"";
        }
        return s;
    }

    // local@domain.tld: no whitespace (Unicode too, hence (?U)), exactly one @, a dot in the domain, no empty
    // labels, none of the specials that need quoting in an address: < > ( ) [ ] , ; : " \
    private static final String NOT_IN_ADDRESS = "\\s@<>()\\[\\],;:\"\\\\";
    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile(
            "(?U)[^" + NOT_IN_ADDRESS + "]+@[^" + NOT_IN_ADDRESS + ".]+(\\.[^" + NOT_IN_ADDRESS + ".]+)+");

    /** Longest address SMTP can carry (RFC 5321 path limit minus the angle brackets). */
    private static final int MAX_EMAIL_LENGTH = 254;

    public static boolean isValidEmail(String email) {
        return email != null && email.length() <= MAX_EMAIL_LENGTH && EMAIL.matcher(email).matches();
    }

    public static int characters(String s) {
        return s == null ? 0 : s.codePointCount(0, s.length());
    }

    /**
     * Null, or only whitespace, counting Unicode spaces such as U+00A0 that trim() and isBlank() keep.
     */
    public static boolean isEmpty(String s) {
        return s == null || s.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c));
    }

    /**
     * ASCII digits only, and small enough for {@link Long#parseLong}.
     */
    public static boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        try {
            Long.parseLong(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Cuts a rating average off at one decimal (CFP-61): 4.25 gives 4.2. Used by /admin/stats only; a talk's own
     * average rounds halves up instead (see RatingSummary.average).
     */
    public static double roundRating(double avg) {
        // The epsilon absorbs floating-point error: (3.3 + 3.3 + 3.3) / 3 is 3.2999999999999994 and must stay 3.3.
        return Math.floor(avg * 10 + 1e-9) / 10.0;
    }

    public static String capitalize(String s) {
        if (isEmpty(s)) {
            return s;
        }
        int first = s.offsetByCodePoints(0, 1); // a whole code point, not half a surrogate pair
        return s.substring(0, first).toUpperCase(Locale.ROOT) + s.substring(first).toLowerCase(Locale.ROOT);
    }
}
