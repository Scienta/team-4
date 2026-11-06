package no.scienta.cfp;

import java.util.Date;
import java.util.List;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * A talk as the API returns it: the {@link Talk} with its co-speakers and ratings joined in. JSON field order
 * follows the components (pinned by ResponseShapesTest).
 *
 * @param submittedOn the reporting tool wants a plain date. Jackson 2 wrote java.sql.Date as toString()
 *                    ("2026-11-06"); Jackson 3 writes a full UTC timestamp, which also shifts the day, so keep
 *                    toString() explicitly.
 */
public record TalkView(
        Long id,
        String title,
        String format,
        String level,
        Long speakerId,
        Integer durationMinutes,
        String status,
        Date submittedAt,
        @JsonSerialize(using = ToStringSerializer.class) java.sql.Date submittedOn,
        Double averageRating,
        int ratingCount,
        List<Long> coSpeakerIds) {

    static TalkView of(Talk talk, List<Long> coSpeakerIds, RatingSummary ratings) {
        long millis = talk.submittedAt().toEpochMilli();
        return new TalkView(
                talk.id(),
                talk.title(),
                talk.format(),
                talk.level(),
                talk.mainSpeakerId(),
                talk.durationMinutes(),
                talk.status().name(),
                new Date(millis),
                Utils.toSqlDate(millis),
                ratings.average(),
                ratings.count(),
                List.copyOf(coSpeakerIds));
    }
}
