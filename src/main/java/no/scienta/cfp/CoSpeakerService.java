package no.scienta.cfp;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adding and removing a talk's co-speakers (rules in {@link CoSpeakers}). */
@Service
public class CoSpeakerService {

    private final CoSpeakerDao coSpeakerDao;
    private final TalkDao talkDao;
    private final SpeakerService speakers;
    private final TalkService talks;

    public CoSpeakerService(CoSpeakerDao coSpeakerDao, TalkDao talkDao, SpeakerService speakers, TalkService talks) {
        this.coSpeakerDao = coSpeakerDao;
        this.talkDao = talkDao;
        this.speakers = speakers;
        this.talks = talks;
    }

    /**
     * Adding someone already there changes nothing. The talk row is locked, so concurrent adds can't pass the max. A
     * live talk counts towards the co-speaker's cap, so their row is locked too (after the talk's) before counting.
     */
    @Transactional
    public TalkView addCoSpeaker(long talkId, long speakerId) {
        Talk talk = talks.lock(talkId);
        speakers.requireExists(speakerId);
        if (coSpeakersOf(talk).mustAdd(speakerId)) {
            if (talk.status().countsTowardsCap()
                    && talkDao.lockSpeakerAndCountLiveTalks(speakerId) >= Submission.MAX_TALKS_PER_SPEAKER) {
                throw Refused.notAllowed("a speaker can be on at most " + Submission.MAX_TALKS_PER_SPEAKER + " talks");
            }
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
