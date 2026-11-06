package no.scienta.cfp;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registering, looking up and updating speakers (rules in {@link Speaker#check}). */
@Service
public class SpeakerService {

    private static final Logger log = LoggerFactory.getLogger(SpeakerService.class);

    private final SpeakerDao speakerDao;
    private final NotificationService notifications;

    public SpeakerService(SpeakerDao speakerDao, NotificationService notifications) {
        this.speakerDao = speakerDao;
        this.notifications = notifications;
    }

    /** One email is one speaker in any case: the pre-check, with the unique index for registrations landing at once. */
    @Transactional
    public Speaker registerSpeaker(SpeakerForm form) {
        Speaker.check(form.getName(), form.getEmail(), form.getBio());
        if (speakerDao.emailTaken(form.getEmail())) {
            throw emailTaken();
        }
        long id;
        try {
            id = speakerDao.insert(form.getName(), form.getEmail(), form.getBio());
        } catch (DuplicateKeyException e) {
            throw emailTaken();
        }
        log.info("registered speaker " + id + " (" + form.getEmail() + ")");
        notifications.speakerRegistered(form.getEmail(), form.getName());
        return new Speaker(id, form.getName(), form.getEmail(), form.getBio());
    }

    public Speaker getSpeaker(long id) {
        Speaker speaker = speakerDao.findById(id);
        if (speaker == null) {
            throw notFound();
        }
        return speaker;
    }

    /** The names of the given speakers, by id, in one query. Any of them missing: not found, like {@link #getSpeaker}. */
    public Map<Long, String> getSpeakerNames(Collection<Long> ids) {
        Map<Long, String> names = speakerDao.names(ids);
        if (names.size() < new HashSet<>(ids).size()) {
            throw notFound();
        }
        return names;
    }

    /** The admin UI fixing typos: same rules as registering. */
    @Transactional
    public Speaker updateSpeaker(long id, String name, String email, String bio) {
        Speaker existing = getSpeaker(id);
        Speaker.check(name, email, bio);
        if (!email.equalsIgnoreCase(existing.email()) && speakerDao.emailTaken(email)) {
            throw emailTaken();
        }
        speakerDao.update(id, name, email, bio);
        return getSpeaker(id);
    }

    public List<Speaker> searchSpeakers(String q) {
        return speakerDao.search(q);
    }

    void requireExists(long id) {
        if (!speakerDao.exists(id)) {
            throw notFound();
        }
    }

    private static Refused notFound() {
        return Refused.notFound("speaker not found");
    }

    private static Refused emailTaken() {
        return Refused.notAllowed("email already registered");
    }
}
