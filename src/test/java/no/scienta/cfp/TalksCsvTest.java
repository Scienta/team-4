package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** The committee's /admin/talks.csv on its own; the full output is pinned by TalkListQueriesTest.csvExport. */
class TalksCsvTest {

    private static final String HEADER = "id;title;format;minutes;level;speaker;status;submitted;rating;ratings\n";

    @Test
    void quotesBecomeApostrophesAndSemicolonsAreQuoted() {
        assertEquals(HEADER + "1;Say 'hi';Presentation;45;Beginner;\"Grace; Hopper\";SUBMITTED;4. nov. 2026;4,5;2\n",
                TalksCsv.write(List.of(talk(1, "Say \"hi\"", 4.5, 2)), Map.of(10L, "Grace; Hopper")));
    }

    @Test
    void lineBreakInATitleIsQuotedInsteadOfBreakingTheRow() {
        assertEquals(HEADER + "1;\"Two\nlines\";Presentation;45;Beginner;Ada;SUBMITTED;4. nov. 2026;;0\n",
                TalksCsv.write(List.of(talk(1, "Two\nlines", null, 0)), Map.of(10L, "Ada")));
    }

    private static TalkView talk(long id, String title, Double average, int count) {
        Date nov4 = Date.from(java.time.Instant.parse("2026-11-04T13:05:00Z"));
        return new TalkView(id, title, "PRESENTATION", "BEGINNER", 10L, 45, "SUBMITTED", nov4,
                new java.sql.Date(nov4.getTime()), average, count, List.of());
    }
}
