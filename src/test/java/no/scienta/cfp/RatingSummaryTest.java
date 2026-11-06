package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** A talk's average (ratings change request, workflow/specs/ratings-cr.md): trimmed at 5+, 2 decimals, banker's. */
class RatingSummaryTest {

    @Test
    void noRatingsNoAverage() {
        assertNull(RatingSummary.NONE.average());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            // fewer than 5: every score counts, 2 decimals
            "5            | 5.0",
            "5,4          | 4.5",
            "4,4,4,5      | 4.25",   // was 4.3 with one decimal
            "5,4,4        | 4.33",
            "4,4,3        | 3.67",
            // 5 or more: one highest and one lowest left out, even with several equal top or bottom scores
            "1,2,3,4,5    | 3.0",    // (2+3+4)/3
            "5,5,5,1,1    | 3.67",   // (5+5+1)/3, only one 5 and one 1 left out
            "5,5,5,5,5    | 5.0",
            "1,4,4,4,4,5  | 4.0",    // (4+4+4+4)/4
            "1,1,5,5,5,5  | 4.0",    // (1+5+5+5)/4
            // banker's rounding at the third decimal: 8 scores left after trimming, so eighths
            "1,3,3,3,3,3,3,3,4,5 | 3.12",  // (3*7+4)/8 = 25/8 = 3.125 -> 3.12 (2 is even)
            "1,3,3,3,3,3,4,4,4,5 | 3.38",  // (3*5+4*3)/8 = 27/8 = 3.375 -> 3.38 (8 is even)
            "1,2,2,2,2,2,2,2,3,5 | 2.12",  // (2*7+3)/8 = 17/8 = 2.125 -> 2.12
            "1,2,2,2,2,2,3,3,3,5 | 2.38",  // (2*5+3*3)/8 = 19/8 = 2.375 -> 2.38
    })
    void average(String scores, double expected) {
        int[] s = Arrays.stream(scores.split(",")).mapToInt(x -> Integer.parseInt(x.trim())).toArray();
        RatingSummary summary = new RatingSummary(s.length, Arrays.stream(s).sum(),
                Arrays.stream(s).min().getAsInt(), Arrays.stream(s).max().getAsInt());
        assertEquals(expected, summary.average());
    }
}
