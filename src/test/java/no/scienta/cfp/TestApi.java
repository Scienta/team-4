package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON over HTTP against the app on a local port, plus the setup steps most tests need (a speaker, a talk).
 */
final class TestApi {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    private final HttpClient http = HttpClient.newHttpClient();
    private final int port;

    TestApi(int port) {
        this.port = port;
    }

    HttpRequest request(String method, String path, String json) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    HttpResponse<String> send(String method, String path, String json) throws Exception {
        return http.send(request(method, path, json), HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> send(String method, String path) throws Exception {
        return send(method, path, null);
    }

    CompletableFuture<HttpResponse<String>> sendAsync(String method, String path, String json) {
        return http.sendAsync(request(method, path, json), HttpResponse.BodyHandlers.ofString());
    }

    /** Registers a speaker with a unique email and returns its id. */
    String newSpeaker() throws Exception {
        HttpResponse<String> res = send("POST", "/speakers",
                "{\"name\":\"Ada Lovelace\",\"email\":\"ada-" + UUID.randomUUID() + "@example.com\"}");
        assertEquals(201, res.statusCode(), res.body());
        return firstId(res.body());
    }

    /** Submits a talk for the speaker and returns its id. */
    String newTalk(String speakerId) throws Exception {
        HttpResponse<String> res = send("POST", "/talks", talk("Talk", speakerId));
        assertEquals(201, res.statusCode(), res.body());
        return firstId(res.body());
    }

    static String talk(String title, String speakerId) {
        return "{\"title\":\"" + title + "\",\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\",\"speakerId\":" + speakerId + "}";
    }

    /** The first {@code "id"} in a JSON body. */
    static String firstId(String body) {
        Matcher m = ID.matcher(body);
        assertTrue(m.find(), body);
        return m.group(1);
    }

    /** How many {@code "id"} fields a JSON body has, e.g. the length of a list of talks. */
    static int countIds(String body) {
        Matcher m = ID.matcher(body);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
