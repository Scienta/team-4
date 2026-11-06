package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.StreamUtils;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void logsMethodPathStatusAndBodiesAndStillSendsTheResponse(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/talks");
        request.setQueryString("dryRun=true");
        request.setContentType("application/json");
        request.setContent("{\n  \"title\": \"Hi\"\n}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            StreamUtils.copyToByteArray(req.getInputStream());
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(201);
            res.getWriter().write("{\"id\":7}");
        });

        assertEquals(201, response.getStatus());
        assertEquals("{\"id\":7}", response.getContentAsString());
        assertTrue(output.getOut().contains(
                "POST /talks?dryRun=true -> 201"), output.getOut());
        assertTrue(output.getOut().contains("req={ \"title\": \"Hi\" }"), output.getOut());
        assertTrue(output.getOut().contains("res={\"id\":7}"), output.getOut());
    }

    /** Who sent it: someone sweeps the programme with DELETEs in production, and we need to see where from. */
    @Test
    void logsTheCallerFromTheProxyHeaderAndTheUserAgent(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/programme/R1/S3");
        request.setRemoteAddr("10.0.0.7");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        request.addHeader("User-Agent", "curl/8.4.0");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204));
        assertTrue(output.getOut().contains("DELETE /programme/R1/S3 -> 204"), output.getOut());
        assertTrue(output.getOut().contains("from=203.0.113.9, 10.0.0.1 ua=\"curl/8.4.0\""), output.getOut());
    }

    @Test
    void withoutProxyHeaderTheSocketAddressAndNoNewlinesFromHeaders(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.setRemoteAddr("10.0.0.7");
        request.addHeader("User-Agent", "evil\nGET /fake -> 200");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });
        assertTrue(output.getOut().contains("from=10.0.0.7 ua=\"evil GET /fake -> 200\""), output.getOut());
    }

    @Test
    void truncatesLongBodies() {
        String s = RequestLoggingFilter.body(new String(new char[5000]).replace('\0', 'x').getBytes(StandardCharsets.UTF_8));
        assertTrue(s.endsWith("…(truncated)"));
        assertEquals(RequestLoggingFilter.MAX_BODY + "…(truncated)".length(), s.length());
    }
}
