package no.scienta.cfp;


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

    static Refused speakerNotFound() {
        return Refused.notFound("speaker not found");
    }

    static Refused talkNotFound() {
        return Refused.notFound("talk not found");
    }
}
