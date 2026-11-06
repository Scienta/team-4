package no.scienta.cfp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Submitting talks and reading them back. Every talk leaves here as a {@link TalkView}, with its co-speakers and
 * ratings fetched in one query each for the whole list.
 */
@Service
public class TalkService {

    private final SpeakerDao speakerDao;
    private final TalkDao talkDao;
    private final RatingDao ratingDao;
    private final CoSpeakerDao coSpeakerDao;
    private final SpeakerService speakers;
    private final NotificationService notifications;

    public TalkService(SpeakerDao speakerDao, TalkDao talkDao, RatingDao ratingDao, CoSpeakerDao coSpeakerDao,
                       SpeakerService speakers, NotificationService notifications) {
        this.speakerDao = speakerDao;
        this.talkDao = talkDao;
        this.ratingDao = ratingDao;
        this.coSpeakerDao = coSpeakerDao;
        this.speakers = speakers;
        this.notifications = notifications;
    }

    /** Rules in {@link Submission}; the speaker must exist and be on fewer than {@link Submission#MAX_TALKS_PER_SPEAKER} live talks. */
    @Transactional
    public TalkView submitTalk(TalkForm form) {
        Submission submission = Submission.of(form);
        if (!speakerDao.exists(submission.speakerId())) {
            throw Refused.invalid("speaker " + submission.speakerId() + " does not exist");
        }
        long id = talkDao.insertIfBelow(Submission.MAX_TALKS_PER_SPEAKER, submission, Instant.now())
                .orElseThrow(() -> Refused.notAllowed(
                        "a speaker can submit at most " + Submission.MAX_TALKS_PER_SPEAKER + " talks"));
        notifications.talkSubmitted(speakerDao.findById(submission.speakerId()).email(), submission.title());
        return getTalk(id);
    }

    public TalkView getTalk(long id) {
        return views(List.of(talkDao.findById(id).orElseThrow(TalkService::notFound))).get(0);
    }

    /** The talks a speaker submitted as main speaker. */
    public List<TalkView> getTalksForSpeaker(long speakerId) {
        speakers.requireExists(speakerId);
        return views(talkDao.findBySpeaker(speakerId));
    }

    /**
     * All talks, or one speaker's. {@code sort=rating}: highest average first, then most ratings, then oldest;
     * unrated talks last, oldest first.
     */
    public List<TalkView> listTalks(String sort, Long speakerId) {
        if (sort != null && !sort.equals("rating")) {
            throw Refused.invalid("sort must be 'rating'");
        }
        List<TalkView> talks = views(speakerId == null ? talkDao.findAll() : talkDao.findBySpeaker(speakerId));
        if ("rating".equals(sort)) {
            List<TalkView> sorted = new ArrayList<>(talks);
            sorted.sort(BY_RATING);
            return sorted;
        }
        return talks;
    }

    private static final Comparator<TalkView> BY_RATING = Comparator
            .comparing((TalkView t) -> t.ratingCount() == 0)
            .thenComparing(TalkView::averageRating, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TalkView::ratingCount, Comparator.reverseOrder())
            .thenComparing(TalkView::id);

    // ---------------------------------------------------------------- shared with the other talk services

    /** Locks the talk's row for the rest of the transaction (docs/adr/0001-talk-row-lock.md). */
    Talk lock(long talkId) {
        return talkDao.lock(talkId).orElseThrow(TalkService::notFound);
    }

    private List<TalkView> views(List<Talk> talks) {
        List<Long> ids = talks.stream().map(Talk::id).toList();
        Map<Long, RatingSummary> ratings = ratingDao.summaries(ids);
        Map<Long, List<Long>> coSpeakers = coSpeakerDao.findByTalks(ids);
        return talks.stream()
                .map(t -> TalkView.of(t, coSpeakers.getOrDefault(t.id(), List.of()),
                        ratings.getOrDefault(t.id(), RatingSummary.NONE)))
                .toList();
    }

    private static Refused notFound() {
        return Refused.notFound("talk not found");
    }
}
