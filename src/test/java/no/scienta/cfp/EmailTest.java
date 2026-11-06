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

    @Test
    void codePointsAreCharacters() {
        assertTrue(Utils.characters("😀😀") == 2);
        assertTrue(Utils.characters("Ådne") == 4);
        assertTrue(Utils.characters(null) == 0);
    }
}
