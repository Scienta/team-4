package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The static frontend at /: it is served, and the choices it offers are ones the API accepts, so the page and the
 * backend don't drift apart. Runs the real app on a random port with a throwaway database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FrontendTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void theFrontPageIsServedAsHtml() throws Exception {
        HttpResponse<String> page = send("GET", "/", null);

        assertEquals(200, page.statusCode());
        assertTrue(page.headers().firstValue("Content-Type").orElse("").startsWith("text/html"),
                page.headers().toString());
        assertTrue(page.body().contains("id=\"speaker-form\""), "speaker form missing");
        assertTrue(page.body().contains("id=\"talk-form\""), "talk form missing");
        assertTrue(page.body().contains("id=\"talks\""), "talk list missing");
    }

    @Test
    void everyFormatAndLevelOfferedByThePageIsAccepted() throws Exception {
        String page = send("GET", "/", null).body();
        List<String> formats = options(page, "t-format");
        List<String> levels = options(page, "t-level");
        assertFalse(formats.isEmpty(), "no formats on the page");
        assertFalse(levels.isEmpty(), "no levels on the page");

        // one speaker per talk, so the limit of three talks per speaker doesn't get in the way
        int n = Math.max(formats.size(), levels.size());
        for (int i = 0; i < n; i++) {
            String format = formats.get(i % formats.size());
            String level = levels.get(i % levels.size());
            long speaker = registerSpeaker();
            HttpResponse<String> talk = send("POST", "/talks", "{\"title\": \"Talk " + i + "\", \"format\": \""
                    + format + "\", \"level\": \"" + level + "\", \"speakerId\": " + speaker + "}");
            assertEquals(201, talk.statusCode(), format + "/" + level + ": " + talk.body());
        }
    }

    @Test
    void thePageDoesNotShadowTheApi() throws Exception {
        HttpResponse<String> talks = send("GET", "/talks", null);

        assertEquals(200, talks.statusCode());
        assertTrue(talks.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),
                talks.headers().toString());
    }

    /** The option values of the select with the given id. */
    private static List<String> options(String page, String selectId) {
        Matcher select = Pattern.compile("<select id=\"" + selectId + "\"[^>]*>(.*?)</select>", Pattern.DOTALL)
                .matcher(page);
        assertTrue(select.find(), "no select #" + selectId);
        List<String> values = new ArrayList<>();
        Matcher option = Pattern.compile("<option value=\"([^\"]+)\"").matcher(select.group(1));
        while (option.find()) {
            values.add(option.group(1));
        }
        return values;
    }

    private long registerSpeaker() throws Exception {
        HttpResponse<String> res = send("POST", "/speakers",
                "{\"name\": \"Grace Hopper\", \"email\": \"grace-" + UUID.randomUUID() + "@example.com\"}");
        assertEquals(201, res.statusCode(), res.body());
        Matcher id = Pattern.compile("\"id\":(\\d+)").matcher(res.body());
        assertTrue(id.find(), res.body());
        return Long.parseLong(id.group(1));
    }

    private HttpResponse<String> send(String method, String path, String json) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (json == null) {
            req.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            req.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }
}
