package no.scienta.cfp;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Registrations for workshops, in the order they registered (by id). Emails compare by {@code lower(email)}. */
@Repository
public class WorkshopRegistrationDao {

    private final JdbcTemplate jdbc;

    private final RowMapper<Registration> mapper = (rs, rowNum) ->
            new Registration(rs.getLong("id"), rs.getLong("talk_id"), rs.getString("name"), rs.getString("email"));

    public WorkshopRegistrationDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Registration> findByWorkshop(long talkId) {
        return jdbc.query("SELECT id, talk_id, name, email FROM workshop_registration WHERE talk_id = ? ORDER BY id",
                mapper, talkId);
    }

    public int count(long talkId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM workshop_registration WHERE talk_id = ?", Integer.class, talkId);
        return n == null ? 0 : n;
    }

    public boolean emailRegistered(long talkId, String email) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM workshop_registration WHERE talk_id = ? AND lower(email) = lower(?)",
                Integer.class, talkId, email);
        return n != null && n > 0;
    }

    /** Caller holds the workshop's row lock. The unique index on (talk_id, lower(email)) is the backstop. */
    public Registration insert(long talkId, String name, String email) {
        return jdbc.queryForObject(
                "INSERT INTO workshop_registration (talk_id, name, email, registered_at) VALUES (?, ?, ?, ?) "
                        + "RETURNING id, talk_id, name, email",
                mapper, talkId, name, email, System.currentTimeMillis());
    }
}
