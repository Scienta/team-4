package no.scienta.cfp;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SpeakerDao {

    private static final RowMapper<Speaker> SPEAKER = (rs, n) ->
            new Speaker(rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.getString("bio"));

    private final JdbcTemplate jdbc;

    public SpeakerDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Speaker findById(long id) {
        List<Speaker> rows = jdbc.query("SELECT id, name, email, bio FROM speaker WHERE id = ?", SPEAKER, id);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    public boolean exists(long id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM speaker WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    public boolean emailTaken(String email) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM speaker WHERE lower(email) = lower(?)", Integer.class, email);
        return n != null && n > 0;
    }

    public long insert(String name, String email, String bio) {
        return jdbc.queryForObject(
                "INSERT INTO speaker (name, email, bio, created) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, name, email, bio, Utils.today());
    }

    public int update(long id, String name, String email, String bio) {
        return jdbc.update("UPDATE speaker SET name = ?, email = ?, bio = ? WHERE id = ?", name, email, bio, id);
    }

    /** The names of the given speakers that exist, by id, in one query. */
    public Map<Long, String> names(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        jdbc.query("SELECT id, name FROM speaker WHERE id = ANY(?)",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", ids.toArray())),
                rs -> {
                    names.put(rs.getLong("id"), rs.getString("name"));
                });
        return names;
    }

    public int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM speaker", Integer.class);
    }

    // Used by the old admin page to look people up.
    public List<Speaker> search(String name) {
        String literal = name.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return jdbc.query("SELECT id, name, email, bio FROM speaker WHERE name ILIKE ? ORDER BY name", SPEAKER,
                "%" + literal + "%");
    }
}
