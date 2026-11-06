package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The print shop's CSV on its own: RFC 4180, Unix line endings, quoting only when needed (programme-export.md). */
class ProgrammeCsvTest {

    private static final String HEADER = "slot,start,end,room,talkId,title,format,speakers\n";

    @Test
    void emptyProgrammeIsJustTheHeader() {
        assertEquals(HEADER, ProgrammeCsv.write(List.of()));
    }

    @Test
    void rowsInOrderEachEndingInANewline() {
        List<ProgrammeRow> rows = List.of(
                row(Slot.S1, Room.R1, 7, "Plain title", "PRESENTATION", List.of("Grace Hopper", "Ada Lovelace")),
                row(Slot.S4, Room.R3, 9, "Hands on", "WORKSHOP", List.of("Alan Turing")));
        assertEquals(HEADER
                        + "S1,09:00,10:00,R1,7,Plain title,PRESENTATION,Grace Hopper; Ada Lovelace\n"
                        + "S4,13:30,15:30,R3,9,Hands on,WORKSHOP,Alan Turing\n",
                ProgrammeCsv.write(rows));
    }

    @Test
    void quotesOnlyWhenNeeded() {
        assertTitle("plain", "plain");
        assertTitle("with space and ; semicolon", "with space and ; semicolon");
        assertTitle("\"Kotlin, \"\"really\"\" fast\"", "Kotlin, \"really\" fast");
        assertTitle("\"a,b\"", "a,b");
        assertTitle("\"say \"\"hi\"\"\"", "say \"hi\"");
        assertTitle("\"two\nlines\"", "two\nlines");
        assertTitle("\"carriage\rreturn\"", "carriage\rreturn");
        assertTitle("", "");
        assertTitle("=1+1", "=1+1");
        assertTitle("!important", "!important");
        assertTitle("#hashtag", "#hashtag");
        assertTitle(" leading and trailing ", " leading and trailing ");
    }

    /** {@code title} written as {@code expected} in an otherwise plain row. */
    private static void assertTitle(String expected, String title) {
        List<ProgrammeRow> rows = List.of(row(Slot.S1, Room.R1, 1, title, "PRESENTATION", List.of("Ada")));
        assertEquals(HEADER + "S1,09:00,10:00,R1,1," + expected + ",PRESENTATION,Ada\n", ProgrammeCsv.write(rows));
    }

    @Test
    void speakersFieldIsQuotedIfANameNeedsIt() {
        List<ProgrammeRow> rows = List.of(row(Slot.S2, Room.R2, 3, "T", "LIGHTNING_10", List.of("Hopper, Grace", "Ådne Øye")));
        assertEquals(HEADER + "S2,10:15,11:15,R2,3,T,LIGHTNING_10,\"Hopper, Grace; Ådne Øye\"\n", ProgrammeCsv.write(rows));
    }

    private static ProgrammeRow row(Slot slot, Room room, long talkId, String title, String format, List<String> speakers) {
        return new ProgrammeRow(slot.name(), slot.start(), slot.end(), room.name(), talkId, title, format, speakers);
    }
}
