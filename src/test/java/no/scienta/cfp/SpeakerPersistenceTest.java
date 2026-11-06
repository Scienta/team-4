package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/** Spec: registered speakers stay registered after the service is restarted (schema.sql runs again on start-up). */
class SpeakerPersistenceTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aSpeakerSurvivesARestart() throws Exception {
        Map<String, Object> db = TestDatabase.freshProperties();
        String email = "kept-" + UUID.randomUUID() + "@example.com";
        String registered;
        long id;
        try (ConfigurableApplicationContext app = start(db)) {
            HttpResponse<String> res = send(app, "POST", "/speakers", "{\"name\":\"Kept\",\"email\":\"" + email + "\"}");
            assertEquals(201, res.statusCode(), res.body());
            registered = res.body();
            Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(registered);
            m.find();
            id = Long.parseLong(m.group(1));
        }
        try (ConfigurableApplicationContext app = start(db)) {
            HttpResponse<String> res = send(app, "GET", "/speakers/" + id, null);
            assertEquals(200, res.statusCode(), res.body());
            assertEquals(registered, res.body());
            assertEquals(409, send(app, "POST", "/speakers", "{\"name\":\"Again\",\"email\":\"" + email + "\"}").statusCode());
        }
    }

    private static ConfigurableApplicationContext start(Map<String, Object> db) {
        return new SpringApplicationBuilder(CfpApplication.class).properties(db).properties("server.port=0").run();
    }

    private HttpResponse<String> send(ConfigurableApplicationContext app, String method, String path, String json)
            throws Exception {
        int port = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
