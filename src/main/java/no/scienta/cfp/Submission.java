package no.scienta.cfp;

import java.util.Arrays;

/**
 * A speaker proposing a talk, checked against the talks spec. Whether the speaker exists and is under
 * {@link #MAX_TALKS_PER_SPEAKER} needs the database, so {@link TalkService} checks that.
 */
record Submission(String title, Format format, Level level, long speakerId) {

    /**
     * A speaker may be on at most 3 talks. Since the submission cap change request (workflow/specs/submission-cap-cr.md)
     * that counts co-speaking too, and leaves out talks that don't {@link TalkStatus#countsTowardsCap()}.
     */
    static final int MAX_TALKS_PER_SPEAKER = 3;

    static Submission of(TalkForm form) {
        if (Utils.isEmpty(form.title)) {
            throw Refused.invalid("title is required");
        }
        Format format = Format.parse(form.format);
        if (format == null) {
            throw Refused.invalid("format must be one of " + Arrays.toString(Format.values()));
        }
        Level level = Level.parse(form.level);
        if (level == null) {
            throw Refused.invalid("unknown level");
        }
        if (form.speakerId == null) {
            throw Refused.invalid("speakerId is required");
        }
        return new Submission(form.title, format, level, form.speakerId);
    }
}
