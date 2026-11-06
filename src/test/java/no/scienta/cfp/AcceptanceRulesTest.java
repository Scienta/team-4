package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The acceptance state machine on its own: no Spring, no database (workflow/specs/acceptance.md). */
class AcceptanceRulesTest {

    @ParameterizedTest(name = "{0} -> {1} with {2} reviewers")
    @CsvSource({
            "SUBMITTED,  ACCEPTED,   2",
            "SUBMITTED,  ACCEPTED,   5",
            "SUBMITTED,  REJECTED,   0",
            "SUBMITTED,  WAITLISTED, 0",
            "WAITLISTED, ACCEPTED,   2",
            "WAITLISTED, REJECTED,   0",
    })
    void allowedDecisions(String from, String to, int reviewers) {
        assertEquals(to, AcceptanceRules.decide(from, to, reviewers));
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
    void refusedDecisions(String from, String to, int reviewers) {
        assertRefused(AcceptanceRules.Reason.NOT_ALLOWED, () -> AcceptanceRules.decide(from, to, reviewers));
    }

    @ParameterizedTest
    @CsvSource({"SUBMITTED", "WITHDRAWN", "accepted", "MAYBE", "''"})
    void notADecision(String to) {
        assertRefused(AcceptanceRules.Reason.INVALID, () -> AcceptanceRules.decide("SUBMITTED", to, 2));
    }

    @Test
    void decisionMustBeAString() {
        assertRefused(AcceptanceRules.Reason.INVALID, () -> AcceptanceRules.requireDecision(null));
        assertRefused(AcceptanceRules.Reason.INVALID, () -> AcceptanceRules.requireDecision(3));
        assertEquals("REJECTED", AcceptanceRules.requireDecision("REJECTED"));
    }

    @ParameterizedTest
    @CsvSource({"SUBMITTED", "WAITLISTED", "ACCEPTED"})
    void speakerCanWithdraw(String from) {
        assertEquals("WITHDRAWN", AcceptanceRules.withdraw(from, 7, 7));
    }

    @ParameterizedTest
    @CsvSource({"REJECTED", "WITHDRAWN"})
    void cannotWithdrawRejectedOrWithdrawn(String from) {
        assertRefused(AcceptanceRules.Reason.NOT_ALLOWED, () -> AcceptanceRules.withdraw(from, 7, 7));
    }

    @Test
    void onlyTheOwnerCanWithdraw() {
        assertRefused(AcceptanceRules.Reason.NOT_THE_SPEAKER, () -> AcceptanceRules.withdraw("SUBMITTED", 7, 8));
        // Somebody else gets 403 even when the talk couldn't be withdrawn anyway.
        assertRefused(AcceptanceRules.Reason.NOT_THE_SPEAKER, () -> AcceptanceRules.withdraw("REJECTED", 7, 8));
    }

    private static void assertRefused(AcceptanceRules.Reason reason, Runnable action) {
        AcceptanceRules.Refused e = assertThrows(AcceptanceRules.Refused.class, action::run);
        assertEquals(reason, e.reason);
    }
}
