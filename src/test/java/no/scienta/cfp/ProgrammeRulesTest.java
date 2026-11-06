package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Putting talks on the programme, on its own: no Spring, no database (workflow/specs/scheduling.md). */
class ProgrammeRulesTest {

    private static final TalkStatus ACCEPTED = new TalkStatus.Accepted();
    private static final String DOES_NOT_FIT = "a workshop needs the long slot";
    private static final String ALREADY_SCHEDULED = "the talk is already on the programme; take it out first to move it";

    // ------------------------------------------------------------ venue

    @Test
    void fixedRoomsAndSlotsWithSignTimes() {
        assertEquals(List.of(Room.R1, Room.R2, Room.R3), List.of(Room.values()));
        assertEquals("09:00", Slot.S1.start());
        assertEquals("10:00", Slot.S1.end());
        assertEquals("10:15", Slot.S2.start());
        assertEquals("11:15", Slot.S2.end());
        assertEquals("11:30", Slot.S3.start());
        assertEquals("12:30", Slot.S3.end());
        assertEquals("13:30", Slot.S4.start());
        assertEquals("15:30", Slot.S4.end());
        assertEquals("15:45", Slot.S5.start());
        assertEquals("16:45", Slot.S5.end());
    }

    @Test
    void parsesExactNamesOnly() {
        assertEquals(Room.R2, Room.parse("R2"));
        assertEquals(Slot.S4, Slot.parse("S4"));
        assertNull(Room.parse("r1"));
        assertNull(Room.parse("R4"));
        assertNull(Slot.parse("S6"));
        assertNull(Slot.parse(null));
    }

    // ------------------------------------------------------------ placing

    @Test
    void acceptedTalkGoesInAFreeSlot() {
        assertTrue(place(talk(1, Format.PRESENTATION, 10), Room.R1, Slot.S1, List.of(), Map.of()));
    }

    @Test
    void sameTalkInSamePlaceChangesNothing() {
        List<ProgrammeEntry> programme = List.of(new ProgrammeEntry(Room.R1, Slot.S1, 1));
        assertFalse(place(talk(1, Format.PRESENTATION, 10), Room.R1, Slot.S1, programme, Map.of(1L, Set.of(10L))));
        // also once the talk has changed (withdrawn): the programme stays as it is
        Candidate withdrawn = new Candidate(1, Format.PRESENTATION, new TalkStatus.Withdrawn(), Set.of(10L));
        assertFalse(place(withdrawn, Room.R1, Slot.S1, programme, Map.of(1L, Set.of(10L))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "WAITLISTED", "REJECTED", "WITHDRAWN"})
    void onlyAcceptedTalks(String status) {
        Candidate c = new Candidate(1, Format.PRESENTATION, TalkStatus.of(status), Set.of(10L));
        assertRefused("only accepted talks go on the programme, this one is " + status, () -> place(c, Room.R1, Slot.S1, List.of(), Map.of()));
    }

    @ParameterizedTest
    @EnumSource(value = Slot.class, names = {"S1", "S2", "S3", "S5"})
    void workshopNeedsTheLongSlot(Slot slot) {
        assertRefused(DOES_NOT_FIT, () -> place(talk(1, Format.WORKSHOP, 10), Room.R1, slot, List.of(), Map.of()));
        assertTrue(place(talk(1, Format.WORKSHOP, 10), Room.R1, Slot.S4, List.of(), Map.of()));
    }

    @ParameterizedTest
    @EnumSource(value = Format.class, names = {"PRESENTATION", "LIGHTNING_20", "LIGHTNING_10"})
    void everythingElseFitsAnywhere(Format format) {
        for (Slot slot : Slot.values()) {
            assertTrue(place(talk(1, format, 10), Room.R1, slot, List.of(), Map.of()), slot.name());
        }
    }

    @Test
    void oneTalkPerRoomAndSlot() {
        List<ProgrammeEntry> programme = List.of(new ProgrammeEntry(Room.R1, Slot.S1, 2));
        assertRefused("R1 S1 already has a talk",
                () -> place(talk(1, Format.PRESENTATION, 10), Room.R1, Slot.S1, programme, Map.of(2L, Set.of(20L))));
    }

    @Test
    void aTalkIsOnTheProgrammeOnce() {
        List<ProgrammeEntry> programme = List.of(new ProgrammeEntry(Room.R2, Slot.S3, 1));
        assertRefused(ALREADY_SCHEDULED,
                () -> place(talk(1, Format.PRESENTATION, 10), Room.R1, Slot.S1, programme, Map.of()));
    }

    @Test
    void nobodyInTwoRoomsAtOnce() {
        List<ProgrammeEntry> programme = List.of(new ProgrammeEntry(Room.R2, Slot.S1, 2));
        // talk 1: main 10, co 11. Talk 2 in R2/S1: main 20, co 11.
        Candidate c = new Candidate(1, Format.PRESENTATION, ACCEPTED, Set.of(10L, 11L));
        assertRefused("a speaker of this talk is already speaking in R2 at S1",
                () -> place(c, Room.R1, Slot.S1, programme, Map.of(2L, Set.of(20L, 11L))));
    }

    @Test
    void samePeopleInDifferentSlotsIsFine() {
        List<ProgrammeEntry> programme = List.of(new ProgrammeEntry(Room.R2, Slot.S2, 2));
        assertTrue(place(talk(1, Format.PRESENTATION, 10), Room.R1, Slot.S1, programme, Map.of(2L, Set.of(10L))));
    }

    @Test
    void refusalOrder() {
        List<ProgrammeEntry> programme = List.of(
                new ProgrammeEntry(Room.R1, Slot.S1, 2),
                new ProgrammeEntry(Room.R3, Slot.S5, 1));
        Map<Long, Set<Long>> people = Map.of(2L, Set.of(10L));
        Candidate rejectedWorkshop = new Candidate(1, Format.WORKSHOP, new TalkStatus.Rejected(), Set.of(10L));
        assertRefused("only accepted talks go on the programme, this one is REJECTED", () -> place(rejectedWorkshop, Room.R1, Slot.S1, programme, people));
        assertRefused(DOES_NOT_FIT, () -> place(talk(1, Format.WORKSHOP, 10), Room.R1, Slot.S1, programme, people));
        assertRefused("R1 S1 already has a talk", () -> place(talk(1, Format.PRESENTATION, 10), Room.R1, Slot.S1, programme, people));
        assertRefused(ALREADY_SCHEDULED, () -> place(talk(1, Format.PRESENTATION, 10), Room.R2, Slot.S1, programme, people));
    }

    /** A talk to place, with its main speaker and co-speakers in {@code people}. */
    private record Candidate(Talk talk, Set<Long> people) {
        Candidate(long id, Format format, TalkStatus status, Set<Long> people) {
            this(new Talk(id, "Talk " + id, format.name(), "BEGINNER", people.iterator().next(), format.durationMinutes,
                    status, Instant.EPOCH), people);
        }
    }

    private static Candidate talk(long id, Format format, long mainSpeaker) {
        return new Candidate(id, format, ACCEPTED, Set.of(mainSpeaker));
    }

    private static boolean place(Candidate c, Room room, Slot slot, List<ProgrammeEntry> programme,
                                 Map<Long, Set<Long>> peopleOf) {
        return ProgrammeRules.place(c.talk(), c.people(), room, slot, programme, peopleOf);
    }

    /** Every refusal is NOT_ALLOWED (409); the message tells them apart. */
    private static void assertRefused(String message, Runnable action) {
        Refused e = assertThrows(Refused.class, action::run);
        assertEquals(Refused.Kind.NOT_ALLOWED, e.kind);
        assertEquals(message, e.getMessage());
    }
}
