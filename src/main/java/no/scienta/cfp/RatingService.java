package no.scienta.cfp;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reviewers rating talks. */
@Service
public class RatingService {

    private final TalkService talks;
    private final RatingDao ratingDao;

    public RatingService(TalkService talks, RatingDao ratingDao) {
        this.talks = talks;
        this.ratingDao = ratingDao;
    }

    @Transactional
    public Rating rate(long talkId, long reviewerId, Map<String, Object> body) {
        if (reviewerId < 1) {
            throw Refused.invalid("reviewerId must be positive");
        }
        Score score = Score.parse(body.get("score"));
        talks.requireExists(talkId);
        String now = Utils.now();
        ratingDao.save(talkId, reviewerId, score, now);
        return new Rating(talkId, reviewerId, score.value(), Utils.usShort(Utils.parse(now)));
    }
}
