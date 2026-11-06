package no.scienta.cfp;

import static no.scienta.cfp.NotFound.speakerNotFound;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registering, looking up and updating speakers. */
@Service
public class SpeakerService {

    private static final Logger log = LoggerFactory.getLogger(SpeakerService.class);

    private final SpeakerDao speakerDao;
    private final NotificationService notifications;

    public SpeakerService(SpeakerDao speakerDao, NotificationService notifications) {
        this.speakerDao = speakerDao;
        this.notifications = notifications;
    }

    static final int MAX_BIO = 500;

    /** The spec's rules for a speaker, shared by registration and the admin update. */
    private static void checkSpeaker(String name, String email, String bio) {
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

    @Transactional
    public Speaker registerSpeaker(SpeakerForm form) {
        checkSpeaker(form.getName(), form.getEmail(), form.getBio());
        if (speakerDao.emailTaken(form.getEmail())) {
            throw Refused.notAllowed("email already registered");
        }
        long id;
        try {
            id = speakerDao.insert(form.getName(), form.getEmail(), form.getBio());
        } catch (DuplicateKeyException e) {
            throw Refused.notAllowed("email already registered");
        }
        log.info("registered speaker " + id + " (" + form.getEmail() + ")");
        notifications.speakerRegistered(form.getEmail(), form.getName());
        return new Speaker(id, form.getName(), form.getEmail(), form.getBio());
    }

    public Speaker getSpeaker(long id) {
        Speaker speaker = speakerDao.findById(id);
        if (speaker == null) {
            throw speakerNotFound();
        }
        return speaker;
    }

    /** The names of the given speakers, by id, in one query. Any of them missing: 404, like {@link #getSpeaker}. */
    public Map<Long, String> getSpeakerNames(Collection<Long> ids) {
        Map<Long, String> names = speakerDao.names(ids);
        if (names.size() < new HashSet<>(ids).size()) {
            throw speakerNotFound();
        }
        return names;
    }

    @Transactional
    public Speaker updateSpeaker(long id, String name, String email, String bio) {
        Speaker existing = getSpeaker(id);
        checkSpeaker(name, email, bio);
        if (!email.equalsIgnoreCase(existing.email()) && speakerDao.emailTaken(email)) {
            throw Refused.notAllowed("email already registered");
        }
        speakerDao.update(id, name, email, bio);
        return getSpeaker(id);
    }

    public List<Speaker> searchSpeakers(String q) {
        return speakerDao.search(q);
    }
}
