package no.scienta.cfp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Logs one line per request: method, path and query, status, duration, the caller (X-Forwarded-For or the socket
 * address, and the User-Agent), and the request and response bodies (truncated). The harness's checks are only visible in the container logs through this.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    static final int MAX_BODY = 2000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper req = new ContentCachingRequestWrapper(request, MAX_BODY);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);
        long started = System.nanoTime();
        Exception failure = null;
        try {
            chain.doFilter(req, res);
        } catch (IOException | ServletException | RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            long ms = (System.nanoTime() - started) / 1_000_000;
            String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
            String line = String.format("%s %s%s -> %d (%d ms) from=%s ua=\"%s\" contentType=%s req=%s res=%s",
                    request.getMethod(), request.getRequestURI(), query,
                    failure == null ? res.getStatus() : 500, ms, caller(request), header(request, "User-Agent"),
                    request.getContentType(),
                    body(req.getContentAsByteArray()), body(res.getContentAsByteArray()));
            if (failure == null) {
                log.info(line);
            } else {
                log.warn(line, failure);
            }
            res.copyBodyToResponse();
        }
    }

    // Error bodies (sendError, ResponseStatusException, …) are rendered in a separate /error dispatch, logged as its own
    // line: "POST /speakers -> 409 … res=-" followed by "POST /error -> 409 … res={…}".
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    /** The client: the proxy's X-Forwarded-For if there is one (the platform terminates TLS), else the socket. */
    static String caller(HttpServletRequest request) {
        String forwarded = header(request, "X-Forwarded-For");
        return forwarded.equals("-") ? request.getRemoteAddr() : forwarded;
    }

    /** A header for the log line: one line (no injected log lines), at most 200 characters, "-" if absent. */
    static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return "-";
        }
        value = value.replaceAll("\\s*[\\r\\n]+\\s*", " ").replace("\"", "'");
        return value.length() > 200 ? value.substring(0, 200) + "…" : value;
    }

    static String body(byte[] bytes) {
        if (bytes.length == 0) {
            return "-";
        }
        String s = new String(bytes, StandardCharsets.UTF_8).replaceAll("\\s*[\\r\\n]+\\s*", " ");
        return s.length() > MAX_BODY ? s.substring(0, MAX_BODY) + "…(truncated)" : s;
    }
}
