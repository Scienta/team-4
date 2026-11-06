package no.scienta.cfp;

import java.util.List;

/**
 * One scheduled talk in the programme export (workflow/specs/programme-export.md), as the JSON list returns it;
 * field order follows the components. The CSV has the same columns ({@link ProgrammeCsv}).
 *
 * @param speakers names: the main speaker first, then the co-speakers in the order they were added
 */
public record ProgrammeRow(String slot, String start, String end, String room, long talkId, String title,
                           String format, List<String> speakers) {

    static ProgrammeRow of(ProgrammeEntry entry, Talk talk, List<String> speakers) {
        return new ProgrammeRow(entry.slot().name(), entry.slot().start(), entry.slot().end(), entry.room().name(),
                talk.id(), talk.title(), talk.format(), List.copyOf(speakers));
    }
}
