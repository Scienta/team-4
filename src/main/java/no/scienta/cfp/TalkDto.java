package no.scienta.cfp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * A talk as the API returns it. Also used internally all over the place.
 */
public class TalkDto {

    public Long id;
    public String title;
    public String format;
    public String level;
    public Long speakerId;
    public Integer durationMinutes;
    public String status;
    public Date submittedAt;
    // The reporting tool wants a plain date. Jackson 2 wrote java.sql.Date as toString() ("2026-11-06"); Jackson 3
    // writes it as a full UTC timestamp, which also shifts the day, so keep toString() explicitly.
    @JsonSerialize(using = ToStringSerializer.class)
    public java.sql.Date submittedOn;
    public Double averageRating;
    public int ratingCount;
    public List<Long> coSpeakerIds = new ArrayList<>();
}
