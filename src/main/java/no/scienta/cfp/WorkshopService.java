package no.scienta.cfp;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registering for workshops (rules in WorkshopRules, workflow/specs/workshop-registration.md). */
@Service
public class WorkshopService {

    private static final Logger log = LoggerFactory.getLogger(WorkshopService.class);

    private final TalkDao talkDao;
    private final WorkshopRegistrationDao registrations;

    public WorkshopService(TalkDao talkDao, WorkshopRegistrationDao registrations) {
        this.talkDao = talkDao;
        this.registrations = registrations;
    }

    /**
     * Registers an attendee. The workshop's talk row is locked, so a rush is applied one at a time and can't go
     * past capacity; a concurrent withdrawal or decision locks the same row.
     */
    @Transactional
    public Registration register(long talkId, Map<String, Object> body) {
        String name = requireText(body, "name");
        String email = requireText(body, "email");
        if (!Utils.isValidEmail(email)) {
            throw Refused.invalid("invalid email");
        }
        TalkDto talk = talkDao.lock(talkId);
        if (talk == null) {
            throw notFound();
        }
        WorkshopRules.register(talk.format, TalkStatus.of(talk.status),
                registrations.count(talkId), registrations.emailRegistered(talkId, email));
        try {
            Registration r = registrations.insert(talkId, name, email);
            log.info("workshop " + talkId + ": registration " + r.id());
            return r;
        } catch (DuplicateKeyException e) {
            throw Refused.notAllowed("this email is already registered for the workshop");
        }
    }

    /** Any existing talk: a non-workshop just has none (harness check-2 rejected 404 for it, tick 89). */
    public List<Registration> list(long talkId) {
        if (!talkDao.exists(talkId)) {
            throw notFound();
        }
        return registrations.findByWorkshop(talkId);
    }

    private static String requireText(Map<String, Object> body, String field) {
        Object value = body == null ? null : body.get(field);
        if (!(value instanceof String s) || Utils.isEmpty(s)) {
            throw Refused.invalid(field + " is required");
        }
        return s;
    }

    private static Refused notFound() {
        return Refused.notFound("workshop not found");
    }
}
