package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Date;
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

    @Test
    void isNumeric() {
        assertTrue(Utils.isNumeric("123"));
        assertFalse(Utils.isNumeric("12a"));
        assertFalse(Utils.isNumeric(""));
    }

    @Test
    void capitalize() {
        assertEquals("Workshop", Utils.capitalize("WORKSHOP"));
    }

    // Ratings spec: one decimal, halves rounded up (4, 4, 4, 5 averages 4.25, shown as 4.3)
    @Test
    void roundsRatingToOneDecimalHalfUp() {
        assertEquals(4.3, Utils.roundRating(4.25));
        assertEquals(3.7, Utils.roundRating(3.6666));
        assertEquals(3.3, Utils.roundRating(3.3333));
        assertEquals(4.5, Utils.roundRating(4.5));
        assertEquals(4.2, Utils.roundRating(4.15));
        assertEquals(5.0, Utils.roundRating(5));
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
