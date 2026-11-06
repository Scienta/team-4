package no.scienta.cfp;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** How many reviewers rated a talk and the sum of their scores. */
record RatingSummary(int count, long total) {

    static final RatingSummary NONE = new RatingSummary(0, 0);

    /**
     * Ratings spec: one decimal, halves rounded up (4.25 gives 4.3); null without ratings. Unlike the /admin/stats
     * average, which is cut off (CFP-61, {@link Utils#roundRating}).
     */
    Double average() {
        if (count == 0) {
            return null;
        }
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP).doubleValue();
    }
}
