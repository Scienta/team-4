package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Blocking a flooding client by User-Agent prefix (workflow/specs/blocked-client.md). The harness uses
 * Java-http-client and the platform probe kube-probe, so neither is blocked; health is exempt whatever the agent.
 */
class BlockedClientFilterTest {

    private final BlockedClientFilter filter = new BlockedClientFilter("curl/8.5.0");

    @Test
    void blocksTheConfiguredUserAgent() throws Exception {
        MockHttpServletResponse response = run("POST", "/talks", "curl/8.5.0");
        assertEquals(403, response.getStatus());
    }

    @Test
    void letsTheHarnessAndProbeThrough() throws Exception {
        assertEquals(200, run("POST", "/talks", "Java-http-client/25.0.5").getStatus());
        assertEquals(200, run("GET", "/health", "kube-probe//").getStatus());
        assertEquals(200, run("POST", "/speakers", null).getStatus());
    }

    @Test
    void healthIsExemptEvenForABlockedAgent() throws Exception {
        assertEquals(200, run("GET", "/health", "curl/8.5.0").getStatus());
    }

    @Test
    void emptyDenylistBlocksNobody() throws Exception {
        BlockedClientFilter off = new BlockedClientFilter("");
        AtomicBoolean passed = new AtomicBoolean();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/talks");
        request.addHeader("User-Agent", "curl/8.5.0");
        off.doFilter(request, new MockHttpServletResponse(), (req, res) -> passed.set(true));
        assertTrue(passed.get());
    }

    @Test
    void matchesByPrefixSoAnyCurlVersionWithTheListedPrefixIsBlocked() {
        BlockedClientFilter byFamily = new BlockedClientFilter("curl/");
        assertTrue(byFamily.blocked("curl/8.5.0"));
        assertTrue(byFamily.blocked("curl/7.1.0"));
        assertFalse(byFamily.blocked("Java-http-client/25.0.5"));
        assertFalse(byFamily.blocked(null));
    }

    private MockHttpServletResponse run(String method, String path, String userAgent) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (userAgent != null) {
            request.addHeader("User-Agent", userAgent);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(200));
        return response;
    }
}
