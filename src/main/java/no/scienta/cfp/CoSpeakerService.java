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

    /** Adding someone already there changes nothing. The talk row is locked, so concurrent adds can't pass the max. */
    @Transactional
    public TalkView addCoSpeaker(long talkId, long speakerId) {
        Talk talk = talks.lock(talkId);
        speakers.requireExists(speakerId);
        if (coSpeakersOf(talk).mustAdd(speakerId)) {
            coSpeakerDao.add(talkId, speakerId);
        }
        return talks.getTalk(talkId);
    }

    @Transactional
    public TalkView removeCoSpeaker(long talkId, long speakerId) {
        Talk talk = talks.lock(talkId);
        speakers.requireExists(speakerId);
        coSpeakersOf(talk).requireCoSpeaker(speakerId);
        coSpeakerDao.remove(talkId, speakerId);
        return talks.getTalk(talkId);
    }

    private CoSpeakers coSpeakersOf(Talk talk) {
        return new CoSpeakers(talk.mainSpeakerId(), coSpeakerDao.findByTalk(talk.id()));
    }
}
