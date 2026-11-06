package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Who may register for a workshop, on its own: no Spring, no database (workflow/specs/workshop-registration.md).
 * All refusals are {@link Refused.Kind#NOT_ALLOWED} (409); the message says which rule refused.
 */
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
        assertRefused(NOT_A_WORKSHOP, () -> WorkshopRules.register(format, ACCEPTED, 0, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "WAITLISTED", "REJECTED", "WITHDRAWN"})
    void onlyAcceptedWorkshops(String status) {
        assertRefused(NOT_OPEN, () -> WorkshopRules.register("WORKSHOP", TalkStatus.of(status), 0, false));
    }

    @Test
    void twentySeats() {
        assertEquals(20, WorkshopRules.CAPACITY);
        assertRefused(FULL, () -> WorkshopRules.register("WORKSHOP", ACCEPTED, 20, false));
    }

    @Test
    void oneRegistrationPerEmail() {
        assertRefused(DUPLICATE, () -> WorkshopRules.register("WORKSHOP", ACCEPTED, 3, true));
    }

    @Test
    void refusalOrder() {
        assertRefused(NOT_A_WORKSHOP, () -> WorkshopRules.register("PRESENTATION", TalkStatus.of("SUBMITTED"), 20, true));
        assertRefused(NOT_OPEN, () -> WorkshopRules.register("WORKSHOP", TalkStatus.of("WITHDRAWN"), 20, true));
        // Already registered when it's full: say "already registered", not "full".
        assertRefused(DUPLICATE, () -> WorkshopRules.register("WORKSHOP", ACCEPTED, 20, true));
    }

    private static final String NOT_A_WORKSHOP = "not a workshop";
    private static final String NOT_OPEN = "workshop takes no registrations";
    private static final String DUPLICATE = "this email is already registered for the workshop";
    private static final String FULL = "the workshop is full (20 attendees)";

    private static void assertRefused(String message, Runnable action) {
        Refused e = assertThrows(Refused.class, action::run);
        assertEquals(Refused.Kind.NOT_ALLOWED, e.kind);
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }
}
