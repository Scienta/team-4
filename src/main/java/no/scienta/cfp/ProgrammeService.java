package no.scienta.cfp;

import java.util.Objects;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Putting talks into rooms and slots (rules in {@link ProgrammeRules}, workflow/specs/scheduling.md). */
@Service
public class ProgrammeService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammeService.class);

    private final ProgrammeDao programme;
    private final TalkDao talkDao;
    private final CoSpeakerDao coSpeakerDao;
    private final SpeakerDao speakerDao;

    public ProgrammeService(ProgrammeDao programme, TalkDao talkDao, CoSpeakerDao coSpeakerDao, SpeakerDao speakerDao) {
        this.programme = programme;
        this.talkDao = talkDao;
        this.coSpeakerDao = coSpeakerDao;
        this.speakerDao = speakerDao;
    }

    /**
     * The whole programme for the export, like a day: earliest slot first, then by room. Talks and speaker names as
     * they are now. A handful of queries for the whole programme (at most 15 entries).
     */
    public List<ProgrammeRow> export() {
        List<ProgrammeEntry> entries = new ArrayList<>(programme.findAll());
        entries.sort(Comparator.comparing(ProgrammeEntry::slot).thenComparing(ProgrammeEntry::room));
        List<Long> talkIds = entries.stream().map(ProgrammeEntry::talkId).toList();
        Map<Long, Talk> talks = new HashMap<>();
        for (Long id : talkIds) {
            talkDao.findById(id).ifPresent(t -> talks.put(id, t));
        }
        Map<Long, List<Long>> coSpeakers = coSpeakerDao.findByTalks(talkIds);
        Set<Long> speakerIds = new HashSet<>();
        talks.values().forEach(t -> speakerIds.add(t.mainSpeakerId()));
        coSpeakers.values().forEach(speakerIds::addAll);
        Map<Long, String> names = speakerDao.names(speakerIds);

        List<ProgrammeRow> rows = new ArrayList<>();
        for (ProgrammeEntry e : entries) {
            Talk talk = talks.get(e.talkId());
            if (talk == null) {
                continue; // talks are never deleted; nothing to show if one ever is
            }
            List<String> speakers = new ArrayList<>();
            speakers.add(names.get(talk.mainSpeakerId()));
            coSpeakers.getOrDefault(talk.id(), List.of()).forEach(id -> speakers.add(names.get(id)));
            speakers.removeIf(Objects::isNull);
            rows.add(ProgrammeRow.of(e, talk, speakers));
        }
        return rows;
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
            throw Refused.invalid("talkId must be a whole number");
        }
        long talkId = ((Number) raw).longValue();
        Talk talk = talkDao.lock(talkId)
                .orElseThrow(() -> Refused.invalid("talk " + talkId + " does not exist"));
        programme.lockSlot(slot);

        List<ProgrammeEntry> entries = programme.findAll();
        Set<Long> inSlot = new HashSet<>();
        entries.stream().filter(e -> e.slot() == slot).forEach(e -> inSlot.add(e.talkId()));
        Map<Long, Set<Long>> peopleOf = people(inSlot);
        ProgrammeEntry entry = new ProgrammeEntry(room, slot, talkId);
        if (ProgrammeRules.place(talk, people(talk.id(), talk.mainSpeakerId()), room, slot, entries, peopleOf)) {
            try {
                programme.insert(entry);
            } catch (DuplicateKeyException e) {
                throw Refused.notAllowed("room and slot taken, or talk already on the programme");
            }
            log.info("programme: talk " + talkId + " in " + room + " " + slot);
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
            Set<Long> set = new HashSet<>(coSpeakers.getOrDefault(id, List.of()));
            talkDao.findById(id).ifPresent(t -> set.add(t.mainSpeakerId()));
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
            throw Refused.notFound("no room " + name);
        }
        return room;
    }

    private static Slot slot(String name) {
        Slot slot = Slot.parse(name);
        if (slot == null) {
            throw Refused.notFound("no slot " + name);
        }
        return slot;
    }

    private static Refused empty() {
        return Refused.notFound("no talk in this room and slot");
    }
}
