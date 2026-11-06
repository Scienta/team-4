package no.scienta.cfp;

import static no.scienta.cfp.NotFound.talkNotFound;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Adding and removing a talk's co-speakers. */
@Service
public class CoSpeakerService {

    private final SpeakerDao speakerDao;
    private final TalkDao talkDao;
    private final CoSpeakerDao coSpeakerDao;
    private final TalkService talks;

    public CoSpeakerService(SpeakerDao speakerDao, TalkDao talkDao, CoSpeakerDao coSpeakerDao, TalkService talks) {
        this.speakerDao = speakerDao;
        this.talkDao = talkDao;
        this.coSpeakerDao = coSpeakerDao;
        this.talks = talks;
    }

    static final int MAX_CO_SPEAKERS = 2;

    /**
     * Adds a co-speaker. Already a co-speaker: unchanged. The talk row is locked so concurrent adds can't exceed the max.
     */
    @Transactional
    public TalkDto addCoSpeaker(long talkId, long speakerId) {
        Long mainSpeaker = lockTalk(talkId);
        NotFound.requireSpeaker(speakerDao, speakerId);
        if (mainSpeaker == speakerId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the main speaker can't also be a co-speaker");
        }
        List<Long> current = coSpeakerDao.findByTalk(talkId);
        if (!current.contains(speakerId)) {
            if (current.size() >= MAX_CO_SPEAKERS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "a talk has at most " + MAX_CO_SPEAKERS + " co-speakers");
            }
            coSpeakerDao.add(talkId, speakerId);
        }
        return talks.getTalk(talkId);
    }

    /** Removes a co-speaker. Not a co-speaker of this talk: 404. */
    @Transactional
    public TalkDto removeCoSpeaker(long talkId, long speakerId) {
        lockTalk(talkId);
        NotFound.requireSpeaker(speakerDao, speakerId);
        if (!coSpeakerDao.findByTalk(talkId).contains(speakerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "speaker is not a co-speaker of this talk");
        }
        coSpeakerDao.remove(talkId, speakerId);
        return talks.getTalk(talkId);
    }

    private Long lockTalk(long talkId) {
        Long mainSpeaker = talkDao.lockMainSpeaker(talkId);
        if (mainSpeaker == null) {
            throw talkNotFound();
        }
        return mainSpeaker;
    }
}
