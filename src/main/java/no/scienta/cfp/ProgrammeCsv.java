package no.scienta.cfp;

import java.util.List;

import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;
import tools.jackson.dataformat.csv.CsvWriteFeature;

/**
 * The programme as the print shop's CSV (workflow/specs/programme-export.md): RFC 4180 with Unix line endings,
 * every line (the header too) ending in {@code \n}, no blank line at the end, fields quoted only when they have to
 * be (a comma, a quote or a line break), speakers in one field joined with {@code "; "}. Written by Jackson CSV;
 * ProgrammeCsvTest pins the exact output.
 */
final class ProgrammeCsv {

    /** Strict quoting: without it Jackson also quotes some fields that don't need it. */
    private static final CsvMapper MAPPER = CsvMapper.builder()
            .enable(CsvWriteFeature.STRICT_CHECK_FOR_QUOTING)
            .enable(CsvWriteFeature.WRITE_LINEFEED_AFTER_LAST_ROW)
            .build();

    /** Columns in {@link ProgrammeRow}'s order; the header line is their names. */
    private static final CsvSchema SCHEMA = CsvSchema.builder()
            .addColumn("slot")
            .addColumn("start")
            .addColumn("end")
            .addColumn("room")
            .addNumberColumn("talkId")
            .addColumn("title")
            .addColumn("format")
            .addArrayColumn("speakers", "; ")
            .setUseHeader(true)
            .setLineSeparator("\n")
            .build();

    private ProgrammeCsv() {
    }

    static String write(List<ProgrammeRow> rows) {
        return MAPPER.writer(SCHEMA).writeValueAsString(rows);
    }
}
