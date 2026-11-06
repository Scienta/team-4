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
     * co-speakers or its workshop registrations takes this lock first.
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
     * Inserts the talk only if its speaker has fewer than {@code max} talks. Returns the new id, or empty.
     * The speaker row is locked first so two submissions for the same speaker can't both see room under the limit.
     */
    @Transactional
    public Optional<Long> insertIfBelow(int max, Submission s, Instant submittedAt) {
        jdbc.queryForList("SELECT id FROM speaker WHERE id = ? FOR UPDATE", s.speakerId());
        List<Long> ids = jdbc.queryForList(
                "INSERT INTO talk (title, format, duration, level, status, speaker_id, submitted_at) "
                        + "SELECT ?, ?, ?, ?, 'SUBMITTED', ?, ? "
                        + "WHERE (SELECT COUNT(*) FROM talk WHERE speaker_id = ?) < ? RETURNING id",
                Long.class, s.title(), s.format().name(), s.format().durationMinutes, s.level().name(), s.speakerId(),
                submittedAt.toEpochMilli(), s.speakerId(), max);
        return ids.stream().findFirst();
    }
}
