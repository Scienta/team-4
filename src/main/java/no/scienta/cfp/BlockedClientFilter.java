package no.scienta.cfp;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects a client whose User-Agent starts with one of {@code cfp.block.user-agents} (comma-separated prefixes),
 * so a flood from a scripted client can't keep taking the shared programme cells the harness's checks need.
 *
 * <p>Deliberately narrow and reversible: the harness uses {@code Java-http-client} and the platform probe
 * {@code kube-probe}, so neither is blocked, and {@code /health} is exempt whatever the agent (the probe must never
 * get a non-200). The match is by User-Agent only, which a caller can change; empty config (the default) blocks
 * nobody. The block is spec-visible — any real client with a listed agent is turned away — so keep the list to the
 * exact flood and empty it (set the env var to "") the moment the flood stops. See workflow/specs/blocked-client.md.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class BlockedClientFilter extends OncePerRequestFilter {

    private final List<String> prefixes;

    public BlockedClientFilter(@Value("${cfp.block.user-agents:}") String userAgents) {
        this.prefixes = userAgents == null || userAgents.isBlank() ? List.of()
                : List.of(userAgents.split("\\s*,\\s*"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"/health".equals(request.getRequestURI()) && blocked(request.getHeader("User-Agent"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    boolean blocked(String userAgent) {
        if (userAgent == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (!prefix.isEmpty() && userAgent.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
