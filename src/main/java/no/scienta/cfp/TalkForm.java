package no.scienta.cfp;

/**
 * Request body for POST /talks. Validated in TalkService (the annotations didn't work with the
 * enum strings, see CFP-87).
 */
public class TalkForm {

    public String title;
    public String format;
    public String level;
    public Long speakerId;
    // public Integer durationMinutes;  // clients must not choose the duration any more
}
