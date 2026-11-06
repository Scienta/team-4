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

    /**
     * Checks the rules for putting {@code talk} in {@code room} and {@code slot}, in this order: already exactly
     * there (nothing to do), accepted, fits the slot, room and slot free, not elsewhere on the programme, nobody in
     * two rooms at once. Every refusal is NOT_ALLOWED (409).
     *
     * @param people    main speaker and co-speakers of {@code talk}
     * @param programme every entry on the programme
     * @param peopleOf  main speaker and co-speakers of each talk on the programme in this slot
     * @return true if the entry must be inserted, false if the talk is already there
     */
    static boolean place(Talk talk, Set<Long> people, Room room, Slot slot, List<ProgrammeEntry> programme,
                         Map<Long, Set<Long>> peopleOf) {
        ProgrammeEntry here = programme.stream()
                .filter(e -> e.room() == room && e.slot() == slot).findFirst().orElse(null);
        if (here != null && here.talkId() == talk.id()) {
            return false;
        }
        if (!(talk.status() instanceof TalkStatus.Accepted)) {
            throw Refused.notAllowed("only accepted talks go on the programme, this one is " + talk.status().name());
        }
        if (talk.isWorkshop() && !slot.isLong()) {
            throw Refused.notAllowed("a workshop needs the long slot");
        }
        if (here != null) {
            throw Refused.notAllowed(room + " " + slot + " already has a talk");
        }
        if (programme.stream().anyMatch(e -> e.talkId() == talk.id())) {
            throw Refused.notAllowed("the talk is already on the programme; take it out first to move it");
        }
        for (ProgrammeEntry other : programme) {
            if (other.slot() == slot
                    && !Collections.disjoint(people, peopleOf.getOrDefault(other.talkId(), Set.of()))) {
                throw Refused.notAllowed("a speaker of this talk is already speaking in " + other.room() + " at " + slot);
            }
        }
        return true;
    }
}
