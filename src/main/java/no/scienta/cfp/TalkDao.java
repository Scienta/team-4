package no.scienta.cfp;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Talks in the {@code talk} table. Rows become {@link Talk}s; the status column is a {@link TalkStatus} name. */
@Repository
public class TalkDao {

    private static final String COLUMNS = "id, title, format, duration, level, status, speaker_id, submitted_at";

    private static final RowMapper<Talk> TALK = (rs, rowNum) -> new Talk(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("format"),
            rs.getString("level"),
            rs.getLong("speaker_id"),
            rs.getInt("duration"),
            TalkStatus.of(rs.getString("status")),
            Instant.ofEpochMilli(rs.getLong("submitted_at")));

    private final JdbcTemplate jdbc;

    public TalkDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Talk> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM talk WHERE id = ?", TALK, id).stream().findFirst();
    }

    /** Talks with this speaker as main speaker, oldest first. */
    public List<Talk> findBySpeaker(long speakerId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM talk WHERE speaker_id = ? ORDER BY id", TALK, speakerId);
    }

    public List<Talk> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM talk ORDER BY id", TALK);
    }

    /**
     * Locks the talk row for the rest of the transaction and returns the talk. Every change to a talk, its
     * co-speakers or its workshop registrations takes this lock first (docs/adr/0001-talk-row-lock.md).
     */
    public Optional<Talk> lock(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM talk WHERE id = ? FOR UPDATE", TALK, id).stream().findFirst();
    }

    public void updateStatus(long id, TalkStatus status) {
        jdbc.update("UPDATE talk SET status = ? WHERE id = ?", status.name(), id);
    }

    public boolean exists(long id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM talk WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    /**
     * How many live talks (not rejected or withdrawn) the speaker is on, as main speaker or co-speaker
     * (change request: submission cap, workflow/specs/submission-cap-cr.md). Takes the speaker id twice.
     */
    private static final String LIVE_TALKS_OF_SPEAKER = "(SELECT COUNT(*) FROM talk t WHERE t.status NOT IN ('REJECTED', 'WITHDRAWN') "
            + "AND (t.speaker_id = ? OR t.id IN (SELECT c.talk_id FROM talk_co_speaker c WHERE c.speaker_id = ?)))";

    /**
     * Inserts the talk only if its speaker is on fewer than {@code max} live talks. Locks the speaker's row first, so
     * concurrent submissions and co-speaker adds for the same speaker are counted one at a time.
     */
    @Transactional
    public Optional<Long> insertIfBelow(int max, Submission s, Instant submittedAt) {
        lockSpeaker(s.speakerId());
        List<Long> ids = jdbc.queryForList(
                "INSERT INTO talk (title, format, duration, level, status, speaker_id, submitted_at) "
                        + "SELECT ?, ?, ?, ?, 'SUBMITTED', ?, ? "
                        + "WHERE " + LIVE_TALKS_OF_SPEAKER + " < ? RETURNING id",
                Long.class, s.title(), s.format().name(), s.format().durationMinutes, s.level().name(), s.speakerId(),
                submittedAt.toEpochMilli(), s.speakerId(), s.speakerId(), max);
        return ids.stream().findFirst();
    }

    /**
     * Locks the speaker's row and counts the live talks they are on (see {@link #insertIfBelow}). The caller adding a
     * co-speaker holds the talk's row lock already: lock order is always talk, then speaker.
     */
    public int lockSpeakerAndCountLiveTalks(long speakerId) {
        lockSpeaker(speakerId);
        return jdbc.queryForObject("SELECT " + LIVE_TALKS_OF_SPEAKER, Integer.class, speakerId, speakerId);
    }

    private void lockSpeaker(long speakerId) {
        jdbc.queryForList("SELECT id FROM speaker WHERE id = ? FOR UPDATE", speakerId);
    }
}
