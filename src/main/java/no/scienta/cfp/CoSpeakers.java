package no.scienta.cfp;

import java.util.List;

/** A talk's co-speakers, in the order they were added: at most {@link #MAX}, never the main speaker. */
record CoSpeakers(long mainSpeakerId, List<Long> speakerIds) {

    static final int MAX = 2;

    /** Must {@code speakerId} be added? False if already a co-speaker (adding again changes nothing). */
    boolean mustAdd(long speakerId) {
        if (speakerId == mainSpeakerId) {
            throw Refused.invalid("the main speaker can't also be a co-speaker");
        }
        if (speakerIds.contains(speakerId)) {
            return false;
        }
        if (speakerIds.size() >= MAX) {
            throw Refused.notAllowed("a talk has at most " + MAX + " co-speakers");
        }
        return true;
    }

    void requireCoSpeaker(long speakerId) {
        if (!speakerIds.contains(speakerId)) {
            throw Refused.notFound("speaker is not a co-speaker of this talk");
        }
    }
}
