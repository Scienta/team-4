package no.scienta.cfp;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A talk's ratings in short: how many reviewers rated it, the sum of their scores, and the lowest and highest score.
 * Enough for the average without loading every rating.
 */
record RatingSummary(int count, long total, int min, int max) {

    static final RatingSummary NONE = new RatingSummary(0, 0, 0, 0);

    /** From this many ratings on, the average leaves out one highest and one lowest score (ratings change request). */
    static final int TRIM_FROM = 5;

    /**
     * Ratings change request (workflow/specs/ratings-cr.md): from {@link #TRIM_FROM} ratings, exactly one highest and
     * one lowest score are left out; 2 decimals, ties at the third decimal to the even neighbour (3.125 gives 3.12).
     * Exact decimal division, so no floating-point error decides a tie. Null without ratings. Unlike the /admin/stats
     * average, which is cut off ({@link Utils#roundRating}, CFP-61).
     */
    Double average() {
        if (count == 0) {
            return null;
        }
        long sum = total;
        int n = count;
        if (count >= TRIM_FROM) {
            sum -= min + max;
            n -= 2;
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_EVEN).doubleValue();
    }
}
