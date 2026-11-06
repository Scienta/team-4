package no.scienta.cfp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** The co-speakers of each of the given talks, in one query, ordered as {@link #findByTalk}. Talks without any are left out. */
    public Map<Long, List<Long>> findByTalks(Collection<Long> talkIds) {
        Map<Long, List<Long>> coSpeakers = new HashMap<>();
        if (talkIds.isEmpty()) {
            return coSpeakers;
        }
        jdbc.query("SELECT talk_id, speaker_id FROM talk_co_speaker WHERE talk_id = ANY(?) ORDER BY id",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", talkIds.toArray())),
                rs -> {
                    coSpeakers.computeIfAbsent(rs.getLong("talk_id"), k -> new ArrayList<>()).add(rs.getLong("speaker_id"));
                });
        return coSpeakers;
    }

    /** Adds the co-speaker unless already there. Caller holds the talk's row lock. */
    public void add(long talkId, long speakerId) {
        jdbc.update("INSERT INTO talk_co_speaker (talk_id, speaker_id) VALUES (?, ?) ON CONFLICT DO NOTHING", talkId, speakerId);
    }

    public void remove(long talkId, long speakerId) {
        jdbc.update("DELETE FROM talk_co_speaker WHERE talk_id = ? AND speaker_id = ?", talkId, speakerId);
    }
}
