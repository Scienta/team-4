package no.scienta.cfp;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The co-speakers of a talk, in the order they were added. */
@Repository
public class CoSpeakerDao {

    private final JdbcTemplate jdbc;

    public CoSpeakerDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Long> findByTalk(long talkId) {
        return jdbc.queryForList("SELECT speaker_id FROM talk_co_speaker WHERE talk_id = ? ORDER BY id", Long.class, talkId);
    }

    /** Adds the co-speaker unless already there. Caller holds the talk's row lock. */
    public void add(long talkId, long speakerId) {
        jdbc.update("INSERT INTO talk_co_speaker (talk_id, speaker_id) VALUES (?, ?) ON CONFLICT DO NOTHING", talkId, speakerId);
    }

    public void remove(long talkId, long speakerId) {
        jdbc.update("DELETE FROM talk_co_speaker WHERE talk_id = ? AND speaker_id = ?", talkId, speakerId);
    }
}
