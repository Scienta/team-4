package no.scienta.cfp;

/** A speaker (GLOSSARY.md), as stored and as the API returns it; JSON field order follows the components. */
public record Speaker(long id, String name, String email, String bio) {

    static final int MAX_BIO = 500;

    /** The speakers spec's rules, shared by registration and the admin update. */
    static void check(String name, String email, String bio) {
        if (Utils.isEmpty(name)) {
            throw Refused.invalid("name is required");
        }
        if (!Utils.isValidEmail(email)) {
            throw Refused.invalid("invalid email");
        }
        if (Utils.characters(bio) > MAX_BIO) {
            throw Refused.invalid("bio is longer than " + MAX_BIO + " characters");
        }
    }
}
