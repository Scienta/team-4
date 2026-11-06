package no.scienta.cfp;

import java.time.Instant;

/**
 * A talk (GLOSSARY.md): what its main speaker submitted and where it stands. Its co-speakers and ratings are kept
 * beside it ({@link CoSpeakers}, {@link RatingSummary}) and joined in for the API by {@link TalkView}.
 *
 * <p>Format, level and duration are as stored, not re-parsed: the table holds talks from earlier years, submitted
 * under other rules (keynotes, no level), and the stored duration is what the reporting tool reads. New talks get
 * them from a {@link Submission}, so for those they are a {@link Format}'s and a {@link Level}'s name.
 *
 * @param level null only for talks from before levels were required
 */
record Talk(long id, String title, String format, String level, long mainSpeakerId, int durationMinutes,
            TalkStatus status, Instant submittedAt) {

    boolean isWorkshop() {
        return Format.WORKSHOP.name().equals(format);
    }
}
