package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmailTest {

    @Test
    void validEmails() {
        assertTrue(Utils.isValidEmail("ada@example.com"));
        assertTrue(Utils.isValidEmail("Ada.Lovelace+cfp@Mail.Example.NO"));
        assertTrue(Utils.isValidEmail("o'brien@example.ie"));
    }

    @Test
    void invalidEmails() {
        for (String e : new String[] {null, "", " ", "not-an-email", "a@b", "@example.com", "ada@", "ada@example.",
                "ada@.com", " ada@example.com", "ada@example.com ", "a da@example.com", "ada@@example.com",
                "ada@exa mple.com", "ada@example..com"}) {
            assertFalse(Utils.isValidEmail(e), "'" + e + "'");
        }
    }

    // \s without UNICODE_CHARACTER_CLASS missed no-break spaces, and the pattern let through header-breaking
    // characters and addresses no mail server accepts (over 254 characters).
    @Test
    void rejectsUnicodeSpacesSpecialsAndOverlongAddresses() {
        for (String e : new String[] {"a\u00A0b@example.com", "ada@exa\u2003mple.com", "<ada@example.com>",
                "ada,bob@example.com", "ada;bob@example.com", "\"ada\"@example.com", "ada@exam(ple).com",
                "a".repeat(64) + "@" + "b".repeat(186) + ".com"}) {
            assertFalse(Utils.isValidEmail(e), "'" + e + "'");
        }
        assertTrue(Utils.isValidEmail("\u00C5dne@example.no"));
        assertTrue(Utils.isValidEmail("a".repeat(64) + "@" + "b".repeat(185) + ".com")); // exactly 254
    }

    @Test
    void codePointsAreCharacters() {
        assertTrue(Utils.characters("😀😀") == 2);
        assertTrue(Utils.characters("Ådne") == 4);
        assertTrue(Utils.characters(null) == 0);
    }
}
