package no.scienta.cfp;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The one place a domain refusal becomes an HTTP status. {@code sendError} goes through Spring's error page, so the
 * body is the same {@code {timestamp, status, error, message, path}} as every other error.
 */
@RestControllerAdvice
class ApiErrors {

    static HttpStatus status(Refused.Kind kind) {
        return switch (kind) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_PERMITTED -> HttpStatus.FORBIDDEN;
            case NOT_ALLOWED -> HttpStatus.CONFLICT;
        };
    }

    @ExceptionHandler(Refused.class)
    void refused(Refused e, HttpServletResponse response) throws IOException {
        response.sendError(status(e.kind).value(), e.getMessage());
    }

    /**
     * Waited longer than {@code cfp.lock-timeout} for a row another request holds (Postgres 55P03, Spring's
     * {@link org.springframework.dao.CannotAcquireLockException}), or lost a deadlock. The transaction rolled back and
     * the request is fine as it is: 503 with {@code Retry-After} says "busy, try again", not "your request is wrong"
     * (4xx) or "we broke" (500). docs/adr/0001-talk-row-lock.md.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    void busy(PessimisticLockingFailureException e, HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.RETRY_AFTER, "1");
        response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "Another request is changing the same thing; try again");
    }
}
