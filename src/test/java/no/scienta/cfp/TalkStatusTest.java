package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import no.scienta.cfp.TalkStatus.Decision;

/** The talk status state machine on its own: no Spring, no database (workflow/specs/acceptance.md). */
class TalkStatusTest {

    // ------------------------------------------------------------ typed transitions

    @Test
    void typedTransitions() {
        TalkStatus.Waitlisted waitlisted = new TalkStatus.Submitted().waitlist();
        TalkStatus.Accepted accepted = waitlisted.accept(2);
        assertInstanceOf(TalkStatus.Withdrawn.class, accepted.withdraw());
        assertInstanceOf(TalkStatus.Rejected.class, new TalkStatus.Submitted().reject());
    }

    @Test
    void acceptNeedsTwoReviewers() {
        assertRefused(Refused.Kind.NOT_ALLOWED, () -> new TalkStatus.Submitted().accept(1));
        assertRefused(Refused.Kind.NOT_ALLOWED, () -> new TalkStatus.Waitlisted().accept(0));
    }

    // ------------------------------------------------------------ decisions from requests

    @ParameterizedTest(name = "{0} -> {1} with {2} reviewers")
    @CsvSource({
            "SUBMITTED,  ACCEPTED,   2",
            "SUBMITTED,  ACCEPTED,   5",
            "SUBMITTED,  REJECTED,   0",
            "SUBMITTED,  WAITLISTED, 0",
            "WAITLISTED, ACCEPTED,   2",
            "WAITLISTED, REJECTED,   0",
    })
    void allowedDecisions(String from, Decision to, int reviewers) {
        assertEquals(to.name(), TalkStatus.of(from).decide(to, reviewers).name());
    }

    @ParameterizedTest(name = "{0} -> {1} with {2} reviewers")
    @CsvSource({
            "SUBMITTED,  ACCEPTED,   0",
            "SUBMITTED,  ACCEPTED,   1",
            "WAITLISTED, ACCEPTED,   1",
            "WAITLISTED, WAITLISTED, 2",
            "ACCEPTED,   ACCEPTED,   2",
            "ACCEPTED,   REJECTED,   2",
            "ACCEPTED,   WAITLISTED, 2",
            "REJECTED,   ACCEPTED,   2",
            "REJECTED,   REJECTED,   2",
            "REJECTED,   WAITLISTED, 2",
            "WITHDRAWN,  ACCEPTED,   2",
            "WITHDRAWN,  REJECTED,   2",
            "WITHDRAWN,  WAITLISTED, 2",
    })
    void refusedDecisions(String from, Decision to, int reviewers) {
        assertRefused(Refused.Kind.NOT_ALLOWED, () -> TalkStatus.of(from).decide(to, reviewers));
    }

    @Test
    void parsesDecisions() {
        assertEquals(Decision.REJECTED, Decision.parse("REJECTED"));
        for (Object bad : new Object[] {"SUBMITTED", "WITHDRAWN", "accepted", "MAYBE", "", 3, null}) {
            assertRefused(Refused.Kind.INVALID, () -> Decision.parse(bad));
        }
    }

    // ------------------------------------------------------------ withdrawal

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "WAITLISTED", "ACCEPTED"})
    void speakerCanWithdraw(String from) {
        assertEquals("WITHDRAWN", TalkStatus.of(from).withdraw(7, 7).name());
    }

    @ParameterizedTest
    @ValueSource(strings = {"REJECTED", "WITHDRAWN"})
    void cannotWithdrawRejectedOrWithdrawn(String from) {
        assertRefused(Refused.Kind.NOT_ALLOWED, () -> TalkStatus.of(from).withdraw(7, 7));
    }

    @Test
    void onlyTheOwnerCanWithdraw() {
        assertRefused(Refused.Kind.NOT_PERMITTED, () -> new TalkStatus.Submitted().withdraw(7, 8));
        // Somebody else gets 403 even when the talk couldn't be withdrawn anyway.
        assertRefused(Refused.Kind.NOT_PERMITTED, () -> new TalkStatus.Rejected().withdraw(7, 8));
    }

    // ------------------------------------------------------------ storage names

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "WAITLISTED", "ACCEPTED", "REJECTED", "WITHDRAWN"})
    void roundTripsStoredNames(String name) {
        assertEquals(name, TalkStatus.of(name).name());
    }

    @Test
    void unknownStoredNameIsABug() {
        assertThrows(IllegalStateException.class, () -> TalkStatus.of("PENDING"));
    }

    private static void assertRefused(Refused.Kind kind, Runnable action) {
        assertEquals(kind, assertThrows(Refused.class, action::run).kind);
    }
}
