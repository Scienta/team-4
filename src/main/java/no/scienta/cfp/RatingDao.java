package no.scienta.cfp;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RatingDao {

    private final JdbcTemplate jdbc;

    public RatingDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Insert or replace the reviewer's rating. One statement, so two clicks at once can't make two rows.
     */
    public void save(long talkId, long reviewerId, int score, String ratedAt) {
        jdbc.update("INSERT INTO rating (talk_id, reviewer_id, score, rated_at) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (talk_id, reviewer_id) DO UPDATE SET score = excluded.score, rated_at = excluded.rated_at",
                talkId, reviewerId, score, ratedAt);
    }

    /** How many ratings a talk has and the sum of their scores. */
    public record Stats(int count, long total) {
    }

    /**
     * The rating stats of the given talks, in one query. Talks without ratings are left out.
     */
    public Map<Long, Stats> stats(Collection<Long> talkIds) {
        Map<Long, Stats> stats = new HashMap<>();
        if (talkIds.isEmpty()) {
            return stats;
        }
        jdbc.query("SELECT talk_id, COUNT(*) AS cnt, SUM(score) AS total FROM rating WHERE talk_id = ANY(?) GROUP BY talk_id",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", talkIds.toArray())),
                rs -> {
                    stats.put(rs.getLong("talk_id"), new Stats(rs.getInt("cnt"), rs.getLong("total")));
                });
        return stats;
    }

    /** How many different reviewers rated the talk (one row per reviewer, see the primary key). */
    public int reviewers(long talkId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(DISTINCT reviewer_id) FROM rating WHERE talk_id = ?", Integer.class, talkId);
        return n == null ? 0 : n;
    }

    // public double average(long talkId) {
    //     return jdbc.queryForObject("SELECT AVG(score) FROM rating WHERE talk_id = ?", Double.class, talkId);
    // }
}
