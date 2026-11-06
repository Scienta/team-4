package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class UtilsTest {

    @Test
    void isEmpty() {
        assertTrue(Utils.isEmpty(null));
        assertTrue(Utils.isEmpty(""));
        assertTrue(Utils.isEmpty("   "));
        assertFalse(Utils.isEmpty("a"));
    }

    // trim() only strips chars <= U+0020, so a title of one no-break space counted as filled in.
    @Test
    void unicodeSpacesAreEmpty() {
        assertTrue(Utils.isEmpty("\u00A0"));
        assertTrue(Utils.isEmpty(" \u2003\u202F "));
        assertFalse(Utils.isEmpty("\u00A0a"));
    }

    @Test
    void isNumeric() {
        assertTrue(Utils.isNumeric("123"));
        assertFalse(Utils.isNumeric("12a"));
        assertFalse(Utils.isNumeric(""));
    }

    // Character.isDigit accepts any script's digits, and a 20-digit string passed but then overflowed Long.parseLong.
    @Test
    void numericMeansAsciiDigitsThatFitInALong() {
        assertFalse(Utils.isNumeric("\u0661\u0662\u0663"));
        assertFalse(Utils.isNumeric("\uFF11\uFF12"));
        assertTrue(Utils.isNumeric("9223372036854775807"));
        assertFalse(Utils.isNumeric("9223372036854775808"));
        assertFalse(Utils.isNumeric("99999999999999999999"));
    }

    @Test
    void capitalize() {
        assertEquals("Workshop", Utils.capitalize("WORKSHOP"));
    }

    // toUpperCase()/toLowerCase() without a locale turn I into dotless ı under a Turkish default locale.
    @Test
    void capitalizeIgnoresTheDefaultLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("tr", "TR"));
            assertEquals("Lightning talk", Utils.capitalize("LIGHTNING TALK"));
            assertEquals("Intermediate", Utils.capitalize("intermediate"));
        } finally {
            Locale.setDefault(before);
        }
    }

    // substring(0, 1) split a surrogate pair, so a supplementary first letter was never upper-cased.
    @Test
    void capitalizeHandlesSupplementaryCharacters() {
        assertEquals("\uD801\uDC00x", Utils.capitalize("\uD801\uDC28X")); // Deseret long I
    }

    // Averages are shown with one decimal, cut off (CFP-61)
    @Test
    void roundsRatingToOneDecimal() {
        assertEquals(4.2, Utils.roundRating(4.25));
        assertEquals(3.6, Utils.roundRating(3.6666));
        assertEquals(4.5, Utils.roundRating(4.5));
    }

    // /admin/stats averages the talks' own averages; 3.3 + 3.3 + 3.3 / 3 is 3.2999999999999994 in doubles.
    @Test
    void floatingPointErrorDoesNotCostATenth() {
        assertEquals(3.3, Utils.roundRating((3.3 + 3.3 + 3.3) / 3));
        assertEquals(0.7, Utils.roundRating((0.7 + 0.7 + 0.7) / 3));
        assertEquals(4.2, Utils.roundRating(4.29));
    }

    // now() is UTC; today() used the JVM's zone, so speaker.created could be a day off from timestamps.
    @Test
    void todayIsTheUtcDate() {
        TimeZone before = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati")); // UTC+14: a different date for 14h a day
            DateTimeFormatter f = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            String expectedBefore = LocalDate.now(ZoneOffset.UTC).format(f);
            String today = Utils.today();
            String expectedAfter = LocalDate.now(ZoneOffset.UTC).format(f);
            assertTrue(today.equals(expectedBefore) || today.equals(expectedAfter), today);
        } finally {
            TimeZone.setDefault(before);
        }
    }

    // SimpleDateFormat is lenient by default: month 13, day 45 rolled over into 2027.
    @Test
    void parseRejectsImpossibleDates() {
        assertEquals(NOV_4_13_05_UTC, Utils.parse("2026-11-04 13:05:00"));
        assertThrows(IllegalArgumentException.class, () -> Utils.parse("2026-13-45 00:00:00"));
        assertThrows(IllegalArgumentException.class, () -> Utils.parse("not a date"));
    }

    // 2026-11-04T13:05:00Z. JDK 20+ (CLDR 42) puts a narrow no-break space before "PM"; the reviewer UI wants a plain one.
    private static final Date NOV_4_13_05_UTC = new Date(1793797500000L);

    @Test
    void usShortUsesAPlainSpaceBeforeAmPm() {
        assertEquals("11/4/26, 1:05 PM", Utils.usShort(NOV_4_13_05_UTC));
    }

    @Test
    void norwegianDate() {
        assertEquals("4. nov. 2026", Utils.norwegianDate(NOV_4_13_05_UTC));
    }

    @Disabled("flaky on Jenkins")
    @Test
    void nowIsCurrentTime() {
        SimpleDateFormat f = new SimpleDateFormat(Utils.DB_DATE_TIME);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        assertEquals(f.format(new Date()), Utils.now());
    }
}
