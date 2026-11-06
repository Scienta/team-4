package no.scienta.cfp;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Reviewers rating talks. */
@Service
public class RatingService {

    private final TalkDao talkDao;
    private final RatingDao ratingDao;

    public RatingService(TalkDao talkDao, RatingDao ratingDao) {
        this.talkDao = talkDao;
        this.ratingDao = ratingDao;
    }

    @Transactional
    public Rating rate(long talkId, long reviewerId, Map<String, Object> body) {
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
        NotFound.requireTalk(talkDao, talkId);
        String now = Utils.now();
        ratingDao.save(talkId, reviewerId, s, now);
        return new Rating(talkId, reviewerId, s, Utils.usShort(Utils.parse(now)));
    }
}
