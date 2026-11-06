package no.scienta.cfp;

import static no.scienta.cfp.NotFound.talkNotFound;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Submitting talks and reading them back, with the fields that live in other tables filled in. */
@Service
public class TalkService {

    private final SpeakerDao speakerDao;
    private final TalkDao talkDao;
    private final RatingDao ratingDao;
    private final CoSpeakerDao coSpeakerDao;
    private final NotificationService notifications;

    public TalkService(SpeakerDao speakerDao, TalkDao talkDao, RatingDao ratingDao,
                       CoSpeakerDao coSpeakerDao, NotificationService notifications) {
        this.speakerDao = speakerDao;
        this.talkDao = talkDao;
        this.ratingDao = ratingDao;
        this.coSpeakerDao = coSpeakerDao;
        this.notifications = notifications;
    }

    /** Talks spec: a speaker may submit at most 3 talks. */
    static final int MAX_TALKS_PER_SPEAKER = 3;

    @Transactional
    public TalkDto submitTalk(TalkForm form) {
        if (Utils.isEmpty(form.title)) {
            throw Refused.invalid("title is required");
        }
        Format format = Format.parse(form.format);
        if (format == null) {
            throw Refused.invalid("format must be one of " + Arrays.toString(Format.values()));
        }
        if (Level.parse(form.level) == null) {
            throw Refused.invalid("unknown level");
        }
        if (form.speakerId == null) {
            throw Refused.invalid("speakerId is required");
        }
        if (!speakerDao.exists(form.speakerId)) {
            throw Refused.invalid("speaker " + form.speakerId + " does not exist");
        }

        Long id = insertIfBelowLimit(form, format.durationMinutes);
        if (id == null) {
            throw Refused.notAllowed("a speaker can submit at most " + MAX_TALKS_PER_SPEAKER + " talks");
        }
        Speaker speaker = speakerDao.findById(form.speakerId);
        notifications.talkSubmitted(speaker.email(), form.title);
        return getTalk(id);
    }

    /**
     * Inserts the talk if the speaker has fewer than {@link #MAX_TALKS_PER_SPEAKER} talks. Returns its id, or null
     * (nothing inserted) if the speaker is already at the limit.
     */
    private Long insertIfBelowLimit(TalkForm form, int duration) {
        return talkDao.insertIfBelow(MAX_TALKS_PER_SPEAKER, form.title, form.format, duration, form.level, form.speakerId,
                System.currentTimeMillis());
    }

    public TalkDto getTalk(long id) {
        TalkDto talk = talkDao.findById(id);
        if (talk == null) {
            throw talkNotFound();
        }
        decorate(List.of(talk));
        return talk;
    }

    public List<TalkDto> getTalksForSpeaker(long speakerId) {
        NotFound.requireSpeaker(speakerDao, speakerId);
        List<TalkDto> talks = talkDao.findBySpeaker(speakerId);
        decorate(talks);
        return talks;
    }

    public List<TalkDto> listTalks(String sort, Long speakerId) {
        if (sort != null && !sort.equals("rating")) {
            throw Refused.invalid("sort must be 'rating'");
        }
        List<TalkDto> talks = speakerId == null ? talkDao.findAll() : talkDao.findBySpeaker(speakerId);
        decorate(talks);
        if ("rating".equals(sort)) {
            List<TalkDto> rated = new ArrayList<>();
            List<TalkDto> unrated = new ArrayList<>();
            for (TalkDto t : talks) {
                if (t.ratingCount > 0) {
                    rated.add(t);
                } else {
                    unrated.add(t);
                }
            }
            rated.sort((a, b) -> {
                int c = Double.compare(b.averageRating, a.averageRating);
                if (c != 0) {
                    return c;
                }
                c = Integer.compare(b.ratingCount, a.ratingCount);
                if (c != 0) {
                    return c;
                }
                return Long.compare(a.id, b.id);
            });
            rated.addAll(unrated);
            return rated;
        }
        return talks;
    }

    /** Fills in the fields of the talks that live in other tables, with one query per table for all of them. */
    private void decorate(List<TalkDto> talks) {
        List<Long> ids = talks.stream().map(t -> t.id).toList();
        Map<Long, RatingDao.Stats> ratings = ratingDao.stats(ids);
        Map<Long, List<Long>> coSpeakers = coSpeakerDao.findByTalks(ids);
        for (TalkDto talk : talks) {
            addRatings(talk, ratings.getOrDefault(talk.id, new RatingDao.Stats(0, 0)));
            talk.coSpeakerIds = coSpeakers.getOrDefault(talk.id, new ArrayList<>());
        }
    }

    private void addRatings(TalkDto talk, RatingDao.Stats stats) {
        int count = stats.count();
        long total = stats.total();
        talk.ratingCount = count;
        talk.averageRating = count == 0 ? null : calculateAverage(total, count);
    }

    // Ratings spec: 1 decimal, half up (4.25 -> 4.3). Unlike the /admin/stats average, which is cut off (CFP-61).
    private static Double calculateAverage(long total, int count) {
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP).doubleValue();
    }
}
