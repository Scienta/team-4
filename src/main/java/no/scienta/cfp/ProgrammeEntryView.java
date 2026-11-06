package no.scienta.cfp;

/** A programme entry as the API returns it; JSON field order follows the components. */
public record ProgrammeEntryView(String room, String slot, String start, String end, long talkId) {

    static ProgrammeEntryView of(ProgrammeEntry e) {
        return new ProgrammeEntryView(e.room().name(), e.slot().name(), e.slot().start(), e.slot().end(), e.talkId());
    }
}
