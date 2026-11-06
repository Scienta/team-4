package no.scienta.cfp;

import static no.scienta.cfp.NotFound.talkNotFound;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Accepting, rejecting, waitlisting and withdrawing talks (state machine in TalkStatus). */
@Service
public class AcceptanceService {

    private static final Logger log = LoggerFactory.getLogger(AcceptanceService.class);

    private final TalkDao talkDao;
    private final RatingDao ratingDao;
    private final TalkService talks;

    public AcceptanceService(TalkDao talkDao, RatingDao ratingDao, TalkService talks) {
        this.talkDao = talkDao;
        this.ratingDao = ratingDao;
        this.talks = talks;
    }

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
        return talks.getTalk(talk.id);
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
}
