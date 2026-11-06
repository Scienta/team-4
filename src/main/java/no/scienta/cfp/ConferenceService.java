package no.scienta.cfp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Speakers, talks and ratings.
 *
 * TODO split this up (CFP-140)
 */
@Service
public class ConferenceService {

    private static final Logger log = LoggerFactory.getLogger(ConferenceService.class);

    private final SpeakerDao speakerDao;
    private final TalkDao talkDao;
    private final RatingDao ratingDao;
    private final CoSpeakerDao coSpeakerDao;
    private final NotificationService notifications;

    public ConferenceService(SpeakerDao speakerDao, TalkDao talkDao, RatingDao ratingDao,
                             CoSpeakerDao coSpeakerDao, NotificationService notifications) {
        this.speakerDao = speakerDao;
        this.talkDao = talkDao;
        this.ratingDao = ratingDao;
        this.coSpeakerDao = coSpeakerDao;
        this.notifications = notifications;
    }

    // ---------------------------------------------------------------- speakers

    static final int MAX_BIO = 500;

    /** The spec's rules for a speaker, shared by registration and the admin update. */
    private static void checkSpeaker(String name, String email, String bio) {
        if (Utils.isEmpty(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (!Utils.isValidEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid email");
        }
        if (Utils.characters(bio) > MAX_BIO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bio is longer than " + MAX_BIO + " characters");
        }
    }

    @Transactional
    public Map<String, Object> registerSpeaker(SpeakerForm form) {
        checkSpeaker(form.getName(), form.getEmail(), form.getBio());
        if (speakerDao.emailTaken(form.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        long id;
        try {
            id = speakerDao.insert(form.getName(), form.getEmail(), form.getBio());
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        log.info("registered speaker " + id + " (" + form.getEmail() + ")");
        notifications.speakerRegistered(form.getEmail(), form.getName());
        Map<String, Object> speaker = new LinkedHashMap<>();
        speaker.put("id", id);
        speaker.put("name", form.getName());
        speaker.put("email", form.getEmail());
        speaker.put("bio", form.getBio());
        return speaker;
    }

    public Map<String, Object> getSpeaker(long id) {
        Map<String, Object> speaker = speakerDao.findById(id);
        if (speaker == null) {
            throw speakerNotFound();
        }
        return speaker;
    }

    @Transactional
    public Map<String, Object> updateSpeaker(long id, String name, String email, String bio) {
        Map<String, Object> existing = getSpeaker(id);
        checkSpeaker(name, email, bio);
        if (!email.equalsIgnoreCase((String) existing.get("email")) && speakerDao.emailTaken(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        speakerDao.update(id, name, email, bio);
        return getSpeaker(id);
    }

    public List<Map<String, Object>> searchSpeakers(String q) {
        return speakerDao.search(q);
    }

    // ---------------------------------------------------------------- talks

    /** Talks spec: a speaker may submit at most 3 talks. */
    static final int MAX_TALKS_PER_SPEAKER = 3;

    @Transactional
    public TalkDto submitTalk(TalkForm form) {
        if (Utils.isEmpty(form.title)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }
        Format format = Format.parse(form.format);
        if (format == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format must be one of " + Arrays.toString(Format.values()));
        }
        if (Level.parse(form.level) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown level");
        }
        if (form.speakerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "speakerId is required");
        }
        if (!speakerDao.exists(form.speakerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "speaker " + form.speakerId + " does not exist");
        }

        Long id = insertIfBelowLimit(form, format.durationMinutes);
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "a speaker can submit at most " + MAX_TALKS_PER_SPEAKER + " talks");
        }
        Map<String, Object> speaker = speakerDao.findById(form.speakerId);
        notifications.talkSubmitted((String) speaker.get("email"), form.title);
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
        decorate(talk);
        return talk;
    }

    public List<TalkDto> getTalksForSpeaker(long speakerId) {
        requireSpeaker(speakerId);
        List<TalkDto> talks = talkDao.findBySpeaker(speakerId);
        for (TalkDto t : talks) {
            decorate(t);
        }
        return talks;
    }

    public List<TalkDto> listTalks(String sort, Long speakerId) {
        if (sort != null && !sort.equals("rating")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sort must be 'rating'");
        }
        List<TalkDto> talks = speakerId == null ? talkDao.findAll() : talkDao.findBySpeaker(speakerId);
        for (TalkDto t : talks) {
            decorate(t);
        }
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

    /** Fills in the fields of a talk that live in other tables. */
    private void decorate(TalkDto talk) {
        addRatings(talk);
        talk.coSpeakerIds = coSpeakerDao.findByTalk(talk.id);
    }

    private void addRatings(TalkDto talk) {
        Map<String, Object> stats = ratingDao.stats(talk.id);
        int count = ((Number) stats.get("cnt")).intValue();
        long total = ((Number) stats.get("total")).longValue();
        talk.ratingCount = count;
        talk.averageRating = count == 0 ? null : calculateAverage(total, count);
    }

    // Ratings spec: 1 decimal, half up (4.25 -> 4.3). Unlike the /admin/stats average, which is cut off (CFP-61).
    private static Double calculateAverage(long total, int count) {
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP).doubleValue();
    }

    // ---------------------------------------------------------------- co-speakers

    static final int MAX_CO_SPEAKERS = 2;

    /**
     * Adds a co-speaker. Already a co-speaker: unchanged. The talk row is locked so concurrent adds can't exceed the max.
     */
    @Transactional
    public TalkDto addCoSpeaker(long talkId, long speakerId) {
        Long mainSpeaker = lockTalk(talkId);
        requireSpeaker(speakerId);
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
        return getTalk(talkId);
    }

    /** Removes a co-speaker. Not a co-speaker of this talk: 404. */
    @Transactional
    public TalkDto removeCoSpeaker(long talkId, long speakerId) {
        lockTalk(talkId);
        requireSpeaker(speakerId);
        if (!coSpeakerDao.findByTalk(talkId).contains(speakerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "speaker is not a co-speaker of this talk");
        }
        coSpeakerDao.remove(talkId, speakerId);
        return getTalk(talkId);
    }

    private Long lockTalk(long talkId) {
        Long mainSpeaker = talkDao.lockMainSpeaker(talkId);
        if (mainSpeaker == null) {
            throw talkNotFound();
        }
        return mainSpeaker;
    }

    // ---------------------------------------------------------------- acceptance (state machine in TalkStatus)

    /**
     * The committee accepts, rejects or waitlists a talk. The talk row is locked, so two decisions at once are
     * applied one after the other and the second is checked against the first's result.
     */
    @Transactional
    public TalkDto decide(long talkId, Map<String, Object> body) {
        try {
            TalkStatus.Decision decision = TalkStatus.Decision.parse(body == null ? null : body.get("status"));
            TalkDto talk = lockTalkRow(talkId);
            int reviewers = decision == TalkStatus.Decision.ACCEPTED ? ratingDao.reviewers(talkId) : 0;
            return changeStatus(talk, TalkStatus.of(talk.status).decide(decision, reviewers));
        } catch (TalkStatus.Refused e) {
            throw refusal(e);
        }
    }

    /** The talk's main speaker withdraws it. Out for good. */
    @Transactional
    public TalkDto withdraw(long talkId, Map<String, Object> body) {
        Object speakerId = body == null ? null : body.get("speakerId");
        if (!(speakerId instanceof Integer || speakerId instanceof Long)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "speakerId must be a whole number");
        }
        TalkDto talk = lockTalkRow(talkId);
        try {
            return changeStatus(talk, TalkStatus.of(talk.status).withdraw(talk.speakerId, ((Number) speakerId).longValue()));
        } catch (TalkStatus.Refused e) {
            throw refusal(e);
        }
    }

    private TalkDto changeStatus(TalkDto talk, TalkStatus to) {
        talkDao.updateStatus(talk.id, to.name());
        log.info("talk " + talk.id + ": " + talk.status + " -> " + to.name());
        return getTalk(talk.id);
    }

    private static ResponseStatusException refusal(TalkStatus.Refused e) {
        HttpStatus status = switch (e.reason) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_THE_SPEAKER -> HttpStatus.FORBIDDEN;
            case NOT_ALLOWED -> HttpStatus.CONFLICT;
        };
        return new ResponseStatusException(status, e.getMessage());
    }

    private TalkDto lockTalkRow(long talkId) {
        TalkDto talk = talkDao.lock(talkId);
        if (talk == null) {
            throw talkNotFound();
        }
        return talk;
    }

    // ---------------------------------------------------------------- not found

    private void requireSpeaker(long speakerId) {
        if (!speakerDao.exists(speakerId)) {
            throw speakerNotFound();
        }
    }

    private void requireTalk(long talkId) {
        if (!talkDao.exists(talkId)) {
            throw talkNotFound();
        }
    }

    private static ResponseStatusException speakerNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "speaker not found");
    }

    private static ResponseStatusException talkNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
    }

    // ---------------------------------------------------------------- statistics

    public Map<String, Object> statistics() {
        List<TalkDto> talks = listTalks(null, null);
        Map<String, Integer> byFormat = new LinkedHashMap<>();
        Map<String, Integer> byLevel = new LinkedHashMap<>();
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        List<Long> speakersWithTalks = new ArrayList<>();
        int minutes = 0;
        double ratingSum = 0;
        int rated = 0;
        for (TalkDto t : talks) {
            byFormat.put(t.format, byFormat.getOrDefault(t.format, 0) + 1);
            byLevel.put(t.level, byLevel.getOrDefault(t.level, 0) + 1);
            byStatus.put(t.status, byStatus.getOrDefault(t.status, 0) + 1);
            if (!speakersWithTalks.contains(t.speakerId) && speakerDao.findById(t.speakerId) != null) {
                speakersWithTalks.add(t.speakerId);
            }
            minutes += t.durationMinutes;
            if (t.averageRating != null) {
                ratingSum += t.averageRating;
                rated++;
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("speakers", speakerDao.count());
        stats.put("speakersWithTalks", speakersWithTalks.size());
        stats.put("talks", talks.size());
        stats.put("byFormat", byFormat);
        stats.put("byLevel", byLevel);
        stats.put("byStatus", byStatus);
        stats.put("totalHours", minutes / 60);
        stats.put("averageRating", rated == 0 ? null : Utils.roundRating(ratingSum / rated));
        stats.put("generated", Utils.now());
        return stats;
    }

    // ---------------------------------------------------------------- ratings

    @Transactional
    public Map<String, Object> rate(long talkId, long reviewerId, Map<String, Object> body) {
        if (reviewerId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reviewerId must be positive");
        }
        Object score = body.get("score");
        if (!(score instanceof Integer)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "score must be a whole number");
        }
        int s = (Integer) score;
        if (s < 1 || s > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "score must be 1-5");
        }
        requireTalk(talkId);
        String now = Utils.now();
        ratingDao.save(talkId, reviewerId, s, now);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("talkId", talkId);
        result.put("reviewerId", reviewerId);
        result.put("score", s);
        result.put("ratedAtDisplay", Utils.usShort(Utils.parse(now)));
        return result;
    }
}
