package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Who may register for a workshop, on its own: no Spring, no database (workflow/specs/workshop-registration.md). */
class WorkshopRulesTest {

    private static final String NOT_A_WORKSHOP = "not a workshop";
    private static final String DUPLICATE = "this email is already registered for the workshop";
    private static final String FULL = "the workshop is full (20 attendees)";

    @Test
    void acceptedWorkshopWithRoomTakesRegistrations() {
        assertDoesNotThrow(() -> WorkshopRules.register(talk("WORKSHOP", "ACCEPTED"), 0, false));
        assertDoesNotThrow(() -> WorkshopRules.register(talk("WORKSHOP", "ACCEPTED"), 19, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRESENTATION", "LIGHTNING_10", "LIGHTNING_20"})
    void onlyWorkshops(String format) {
        assertRefused(NOT_A_WORKSHOP, () -> WorkshopRules.register(talk(format, "ACCEPTED"), 0, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "WAITLISTED", "REJECTED", "WITHDRAWN"})
    void onlyAcceptedWorkshops(String status) {
        assertRefused("a " + status + " workshop takes no registrations",
                () -> WorkshopRules.register(talk("WORKSHOP", status), 0, false));
    }

    @Test
    void twentySeats() {
        assertEquals(20, WorkshopRules.CAPACITY);
        assertRefused(FULL, () -> WorkshopRules.register(talk("WORKSHOP", "ACCEPTED"), 20, false));
    }

    @Test
    void oneRegistrationPerEmail() {
        assertRefused(DUPLICATE, () -> WorkshopRules.register(talk("WORKSHOP", "ACCEPTED"), 3, true));
    }

    @Test
    void refusalOrder() {
        assertRefused(NOT_A_WORKSHOP, () -> WorkshopRules.register(talk("PRESENTATION", "SUBMITTED"), 20, true));
        assertRefused("a WITHDRAWN workshop takes no registrations",
                () -> WorkshopRules.register(talk("WORKSHOP", "WITHDRAWN"), 20, true));
        // Already registered when it's full: say "already registered", not "full".
        assertRefused(DUPLICATE, () -> WorkshopRules.register(talk("WORKSHOP", "ACCEPTED"), 20, true));
    }

    private static Talk talk(String format, String status) {
        return new Talk(1, "Hands on", format, "BEGINNER", 1, 120, TalkStatus.of(status), Instant.EPOCH);
    }

    /** Every refusal is NOT_ALLOWED (409); the message tells them apart. */
    private static void assertRefused(String message, Runnable action) {
        Refused e = assertThrows(Refused.class, action::run);
        assertEquals(Refused.Kind.NOT_ALLOWED, e.kind);
        assertEquals(message, e.getMessage());
    }
}
