package no.scienta.cfp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Putting talks into rooms and slots (rules in ProgrammeRules, workflow/specs/scheduling.md). */
@Service
public class ProgrammeService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammeService.class);

    private final ProgrammeDao programme;
    private final TalkDao talkDao;
    private final CoSpeakerDao coSpeakerDao;

    public ProgrammeService(ProgrammeDao programme, TalkDao talkDao, CoSpeakerDao coSpeakerDao) {
        this.programme = programme;
        this.talkDao = talkDao;
        this.coSpeakerDao = coSpeakerDao;
    }

    /**
     * Puts a talk in a room and slot. Locks the talk row, then the slot, always in that order: placements in the same
     * slot, and placements of the same talk, run one at a time, so the programme never breaks the rules.
     */
    @Transactional
    public ProgrammeEntryView put(String roomName, String slotName, Map<String, Object> body) {
        Room room = room(roomName);
        Slot slot = slot(slotName);
        Object raw = body == null ? null : body.get("talkId");
        if (!(raw instanceof Integer || raw instanceof Long)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "talkId must be a whole number");
        }
        long talkId = ((Number) raw).longValue();
        TalkDto talk = talkDao.lock(talkId);
        if (talk == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "talk " + talkId + " does not exist");
        }
        programme.lockSlot(slot);

        List<ProgrammeEntry> entries = programme.findAll();
        Set<Long> inSlot = new HashSet<>();
        entries.stream().filter(e -> e.slot() == slot).forEach(e -> inSlot.add(e.talkId()));
        Map<Long, Set<Long>> peopleOf = people(inSlot);
        ProgrammeRules.Candidate candidate = new ProgrammeRules.Candidate(talkId, Format.parse(talk.format),
                TalkStatus.of(talk.status), people(talk.id, talk.speakerId));
        ProgrammeEntry entry = new ProgrammeEntry(room, slot, talkId);
        try {
            if (ProgrammeRules.place(candidate, room, slot, entries, peopleOf)) {
                programme.insert(entry);
                log.info("programme: talk " + talkId + " in " + room + " " + slot);
            }
        } catch (ProgrammeRules.Refused e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "room and slot taken, or talk already on the programme");
        }
        return ProgrammeEntryView.of(entry);
    }

    public ProgrammeEntryView get(String roomName, String slotName) {
        Room room = room(roomName);
        Slot slot = slot(slotName);
        return programme.find(room, slot).map(ProgrammeEntryView::of).orElseThrow(ProgrammeService::empty);
    }

    /** Takes the talk out of the room and slot. It can then go somewhere else. */
    @Transactional
    public void delete(String roomName, String slotName) {
        Room room = room(roomName);
        Slot slot = slot(slotName);
        programme.lockSlot(slot);
        if (!programme.delete(room, slot)) {
            throw empty();
        }
        log.info("programme: " + room + " " + slot + " emptied");
    }

    /** Main speaker and co-speakers of each of the given talks. */
    private Map<Long, Set<Long>> people(Set<Long> talkIds) {
        Map<Long, List<Long>> coSpeakers = coSpeakerDao.findByTalks(talkIds);
        Map<Long, Set<Long>> people = new HashMap<>();
        for (Long id : talkIds) {
            TalkDto t = talkDao.findById(id);
            Set<Long> set = new HashSet<>(coSpeakers.getOrDefault(id, List.of()));
            if (t != null) {
                set.add(t.speakerId);
            }
            people.put(id, set);
        }
        return people;
    }

    private Set<Long> people(long talkId, long mainSpeaker) {
        Set<Long> set = new HashSet<>(coSpeakerDao.findByTalk(talkId));
        set.add(mainSpeaker);
        return set;
    }

    private static Room room(String name) {
        Room room = Room.parse(name);
        if (room == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no room " + name);
        }
        return room;
    }

    private static Slot slot(String name) {
        Slot slot = Slot.parse(name);
        if (slot == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no slot " + name);
        }
        return slot;
    }

    private static ResponseStatusException empty() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "no talk in this room and slot");
    }
}
