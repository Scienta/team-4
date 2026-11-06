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
            ((javax.servlet.http.HttpServletResponse) res).setStatus(201);
            res.getWriter().write("{\"id\":7}");
        });

        assertEquals(201, response.getStatus());
        assertEquals("{\"id\":7}", response.getContentAsString());
        assertTrue(output.getOut().contains(
                "POST /talks?dryRun=true -> 201"), output.getOut());
        assertTrue(output.getOut().contains("req={ \"title\": \"Hi\" }"), output.getOut());
        assertTrue(output.getOut().contains("res={\"id\":7}"), output.getOut());
    }

    @Test
    void truncatesLongBodies() {
        String s = RequestLoggingFilter.body(new String(new char[5000]).replace('\0', 'x').getBytes(StandardCharsets.UTF_8));
        assertTrue(s.endsWith("…(truncated)"));
        assertEquals(RequestLoggingFilter.MAX_BODY + "…(truncated)".length(), s.length());
    }
}
