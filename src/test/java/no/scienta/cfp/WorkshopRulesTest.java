package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import no.scienta.cfp.WorkshopRules.Reason;

/** Who may register for a workshop, on its own: no Spring, no database (workflow/specs/workshop-registration.md). */
class WorkshopRulesTest {

    private static final TalkStatus ACCEPTED = new TalkStatus.Accepted();

    @Test
    void acceptedWorkshopWithRoomTakesRegistrations() {
        assertDoesNotThrow(() -> WorkshopRules.register("WORKSHOP", ACCEPTED, 0, false));
        assertDoesNotThrow(() -> WorkshopRules.register("WORKSHOP", ACCEPTED, 19, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRESENTATION", "LIGHTNING_10", "LIGHTNING_20"})
    void onlyWorkshops(String format) {
        assertRefused(Reason.NOT_A_WORKSHOP, () -> WorkshopRules.register(format, ACCEPTED, 0, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "WAITLISTED", "REJECTED", "WITHDRAWN"})
    void onlyAcceptedWorkshops(String status) {
        assertRefused(Reason.NOT_OPEN, () -> WorkshopRules.register("WORKSHOP", TalkStatus.of(status), 0, false));
    }

    @Test
    void twentySeats() {
        assertEquals(20, WorkshopRules.CAPACITY);
        assertRefused(Reason.FULL, () -> WorkshopRules.register("WORKSHOP", ACCEPTED, 20, false));
    }

    @Test
    void oneRegistrationPerEmail() {
        assertRefused(Reason.DUPLICATE, () -> WorkshopRules.register("WORKSHOP", ACCEPTED, 3, true));
    }

    @Test
    void refusalOrder() {
        assertRefused(Reason.NOT_A_WORKSHOP, () -> WorkshopRules.register("PRESENTATION", TalkStatus.of("SUBMITTED"), 20, true));
        assertRefused(Reason.NOT_OPEN, () -> WorkshopRules.register("WORKSHOP", TalkStatus.of("WITHDRAWN"), 20, true));
        // Already registered when it's full: say "already registered", not "full".
        assertRefused(Reason.DUPLICATE, () -> WorkshopRules.register("WORKSHOP", ACCEPTED, 20, true));
    }

    private static void assertRefused(Reason reason, Runnable action) {
        WorkshopRules.Refused e = assertThrows(WorkshopRules.Refused.class, action::run);
        assertEquals(reason, e.reason);
    }
}
