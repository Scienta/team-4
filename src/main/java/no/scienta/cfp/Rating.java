package no.scienta.cfp;

/** The response to PUT /talks/{id}/ratings/{reviewerId}; JSON field order follows the components. */
public record Rating(long talkId, long reviewerId, int score, String ratedAtDisplay) {
}
