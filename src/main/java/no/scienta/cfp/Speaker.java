package no.scienta.cfp;

/** A speaker as stored and as the API returns it; JSON field order follows the components. */
public record Speaker(long id, String name, String email, String bio) {
}
