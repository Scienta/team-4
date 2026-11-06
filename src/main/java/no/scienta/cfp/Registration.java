package no.scienta.cfp;

/** A workshop registration as the API returns it. Email as the attendee typed it. */
public record Registration(long id, long workshopId, String name, String email) {
}
