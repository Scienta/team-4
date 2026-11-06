package no.scienta.cfp;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The 404s for speakers and talks, shared by the services. */
final class NotFound {

    private NotFound() {
    }

    static void requireSpeaker(SpeakerDao speakerDao, long speakerId) {
        if (!speakerDao.exists(speakerId)) {
            throw speakerNotFound();
        }
    }

    static void requireTalk(TalkDao talkDao, long talkId) {
        if (!talkDao.exists(talkId)) {
            throw talkNotFound();
        }
    }

    static ResponseStatusException speakerNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "speaker not found");
    }

    static ResponseStatusException talkNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "talk not found");
    }
}
