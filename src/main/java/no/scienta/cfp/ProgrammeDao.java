package no.scienta.cfp;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** The programme: at most one talk per room and slot, each talk at most once (enforced by the schema too). */
@Repository
public class ProgrammeDao {

    private static final RowMapper<ProgrammeEntry> ENTRY = (rs, n) ->
            new ProgrammeEntry(Room.valueOf(rs.getString("room")), Slot.valueOf(rs.getString("slot")), rs.getLong("talk_id"));

    private final JdbcTemplate jdbc;

    public ProgrammeDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Locks the slot (in every room) for the rest of the transaction: changes to one slot run one at a time. */
    public void lockSlot(Slot slot) {
        jdbc.queryForList("SELECT slot FROM programme_slot WHERE slot = ? FOR UPDATE", slot.name());
    }

    public List<ProgrammeEntry> findAll() {
        return jdbc.query("SELECT room, slot, talk_id FROM programme_entry", ENTRY);
    }

    public Optional<ProgrammeEntry> find(Room room, Slot slot) {
        return jdbc.query("SELECT room, slot, talk_id FROM programme_entry WHERE room = ? AND slot = ?",
                ENTRY, room.name(), slot.name()).stream().findFirst();
    }

    public void insert(ProgrammeEntry e) {
        jdbc.update("INSERT INTO programme_entry (room, slot, talk_id) VALUES (?, ?, ?)",
                e.room().name(), e.slot().name(), e.talkId());
    }

    /** Returns whether there was a talk to take out. */
    public boolean delete(Room room, Slot slot) {
        return jdbc.update("DELETE FROM programme_entry WHERE room = ? AND slot = ?", room.name(), slot.name()) > 0;
    }
}
