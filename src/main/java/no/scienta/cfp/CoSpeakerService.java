package no.scienta.cfp;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adding and removing a talk's co-speakers (rules in {@link CoSpeakers}). */
@Service
public class CoSpeakerService {

    private final CoSpeakerDao coSpeakerDao;
    private final SpeakerService speakers;
    private final TalkService talks;

    public CoSpeakerService(CoSpeakerDao coSpeakerDao, SpeakerService speakers, TalkService talks) {
        this.coSpeakerDao = coSpeakerDao;
        this.speakers = speakers;
        this.talks = talks;
    }

    /**
     * Adds a co-speaker. Already a co-speaker: unchanged. The talk row is locked so concurrent adds can't exceed the max.
     */
    @Transactional
    public TalkView addCoSpeaker(long talkId, long speakerId) {
        CoSpeakers coSpeakers = lockCoSpeakers(talkId);
        speakers.requireExists(speakerId);
        if (coSpeakers.mustAdd(speakerId)) {
            coSpeakerDao.add(talkId, speakerId);
        }
        return talks.getTalk(talkId);
    }

    /** Removes a co-speaker. Not a co-speaker of this talk: not found. */
    @Transactional
    public TalkView removeCoSpeaker(long talkId, long speakerId) {
        CoSpeakers coSpeakers = lockCoSpeakers(talkId);
        speakers.requireExists(speakerId);
        coSpeakers.requireCoSpeaker(speakerId);
        coSpeakerDao.remove(talkId, speakerId);
        return talks.getTalk(talkId);
    }

    /** Locks the talk's row, then reads its co-speakers, so they can't change until the transaction ends. */
    private CoSpeakers lockCoSpeakers(long talkId) {
        Talk talk = talks.lock(talkId);
        return new CoSpeakers(talk.mainSpeakerId(), coSpeakerDao.findByTalk(talkId));
    }
}
