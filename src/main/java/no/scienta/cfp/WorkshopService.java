package no.scienta.cfp;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid email");
        }
        TalkDto talk = talkDao.lock(talkId);
        if (talk == null) {
            throw notFound();
        }
        try {
            WorkshopRules.register(talk.format, TalkStatus.of(talk.status),
                    registrations.count(talkId), registrations.emailRegistered(talkId, email));
            Registration r = registrations.insert(talkId, name, email);
            log.info("workshop " + talkId + ": registration " + r.id());
            return r;
        } catch (WorkshopRules.Refused e) {
            throw refusal(e);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "this email is already registered for the workshop");
        }
    }

    public List<Registration> list(long talkId) {
        TalkDto talk = talkDao.findById(talkId);
        if (talk == null) {
            throw notFound();
        }
        try {
            WorkshopRules.requireWorkshop(talk.format);
        } catch (WorkshopRules.Refused e) {
            throw refusal(e);
        }
        return registrations.findByWorkshop(talkId);
    }

    private static String requireText(Map<String, Object> body, String field) {
        Object value = body == null ? null : body.get(field);
        if (!(value instanceof String s) || Utils.isEmpty(s)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return s;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "workshop not found");
    }

    private static ResponseStatusException refusal(WorkshopRules.Refused e) {
        HttpStatus status = switch (e.reason) {
            case NOT_A_WORKSHOP -> HttpStatus.NOT_FOUND;
            case NOT_OPEN, DUPLICATE, FULL -> HttpStatus.CONFLICT;
        };
        return new ResponseStatusException(status, e.getMessage());
    }
}
