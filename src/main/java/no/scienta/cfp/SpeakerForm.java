package no.scienta.cfp;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /speakers (bean-validated) and PUT /speakers/{id} (checked by the service only).
 */
public class SpeakerForm {

    @NotBlank
    private String name;

    @NotBlank
    private String email;

    // at most ConferenceService.MAX_BIO characters, checked there: @Size counts an emoji as two
    private String bio;

    // TODO twitter handle, see CFP-112
    // private String twitter;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}
