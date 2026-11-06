package no.scienta.cfp;

import java.util.List;
import java.util.Map;

import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;
import tools.jackson.dataformat.csv.CsvWriteFeature;

/**
 * The committee's spreadsheet export, GET /admin/talks.csv: semicolon-separated, Norwegian dates and decimal commas,
 * pinned by TalkListQueriesTest. Written by Jackson CSV, quoting a field only if it has a semicolon or a line break.
 * Double quotes in a title or name become apostrophes, as the spreadsheet has always had them.
 */
final class TalksCsv {

    /** One line of the export; field order is the column order. */
    record Row(long id, String title, String format, int minutes, String level, String speaker, String status,
               String submitted, String rating, int ratings) {
    }

    private static final CsvMapper MAPPER = CsvMapper.builder()
            .enable(CsvWriteFeature.STRICT_CHECK_FOR_QUOTING)
            .enable(CsvWriteFeature.WRITE_LINEFEED_AFTER_LAST_ROW)
            .build();

    private static final CsvSchema SCHEMA = CsvSchema.builder()
            .addNumberColumn("id")
            .addColumn("title")
            .addColumn("format")
            .addNumberColumn("minutes")
            .addColumn("level")
            .addColumn("speaker")
            .addColumn("status")
            .addColumn("submitted")
            .addColumn("rating")
            .addNumberColumn("ratings")
            .setColumnSeparator(';')
            .setUseHeader(true)
            .setLineSeparator("\n")
            .build();

    private TalksCsv() {
    }

    static String write(List<TalkView> talks, Map<Long, String> speakerNames) {
        List<Row> rows = talks.stream().map(t -> new Row(
                t.id(),
                apostrophes(t.title()),
                Utils.capitalize(t.format().replace("_", " ")),
                t.durationMinutes(),
                Utils.capitalize(t.level()),
                apostrophes(speakerNames.get(t.speakerId())),
                t.status(),
                Utils.norwegianDate(t.submittedAt()),
                t.averageRating() == null ? "" : String.valueOf(t.averageRating()).replace('.', ','),
                t.ratingCount())).toList();
        return MAPPER.writer(SCHEMA).writeValueAsString(rows);
    }

    private static String apostrophes(String s) {
        return s == null ? null : s.replace('"', '\'');
    }
}
