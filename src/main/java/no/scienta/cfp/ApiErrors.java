package no.scienta.cfp;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

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
}
