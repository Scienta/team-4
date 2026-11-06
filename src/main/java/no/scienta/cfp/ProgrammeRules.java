package no.scienta.cfp;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Putting a talk on the programme (workflow/specs/scheduling.md). No database, no HTTP: the caller locks the talk
 * and the slot, loads the programme, asks here, and inserts if told to.
 */
final class ProgrammeRules {

    private ProgrammeRules() {
    }

    /** Why a placement was refused. The caller maps it to a response. */
    enum Reason { NOT_ACCEPTED, DOES_NOT_FIT, TAKEN, ALREADY_SCHEDULED, SPEAKER_CLASH }

    static final class Refused extends RuntimeException {
        final Reason reason;

        Refused(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    /** The talk to place, with its main speaker and co-speakers in {@code people}. */
    record Candidate(long talkId, Format format, TalkStatus status, Set<Long> people) {
    }

    /**
     * Checks the rules for putting {@code talk} in {@code room} and {@code slot}, in this order: already exactly
     * there (nothing to do), accepted, fits the slot, room and slot free, not elsewhere on the programme, nobody in
     * two rooms at once.
     *
     * @param programme every entry on the programme
     * @param peopleOf  main speaker and co-speakers of each talk on the programme in this slot
     * @return true if the entry must be inserted, false if the talk is already there
     */
    static boolean place(Candidate talk, Room room, Slot slot, List<ProgrammeEntry> programme,
                         Map<Long, Set<Long>> peopleOf) {
        ProgrammeEntry here = programme.stream()
                .filter(e -> e.room() == room && e.slot() == slot).findFirst().orElse(null);
        if (here != null && here.talkId() == talk.talkId()) {
            return false;
        }
        if (!(talk.status() instanceof TalkStatus.Accepted)) {
            throw new Refused(Reason.NOT_ACCEPTED, "only accepted talks go on the programme, this one is " + talk.status().name());
        }
        if (talk.format() == Format.WORKSHOP && !slot.isLong()) {
            throw new Refused(Reason.DOES_NOT_FIT, "a workshop needs the long slot");
        }
        if (here != null) {
            throw new Refused(Reason.TAKEN, room + " " + slot + " already has a talk");
        }
        if (programme.stream().anyMatch(e -> e.talkId() == talk.talkId())) {
            throw new Refused(Reason.ALREADY_SCHEDULED, "the talk is already on the programme; take it out first to move it");
        }
        for (ProgrammeEntry other : programme) {
            if (other.slot() == slot
                    && !Collections.disjoint(talk.people(), peopleOf.getOrDefault(other.talkId(), Set.of()))) {
                throw new Refused(Reason.SPEAKER_CLASH, "a speaker of this talk is already speaking in " + other.room() + " at " + slot);
            }
        }
        return true;
    }
}
