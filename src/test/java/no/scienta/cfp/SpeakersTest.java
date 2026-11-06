package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Speaker registration: emails are unique regardless of letter case, also under concurrent registrations, and the
 * old admin search matches names regardless of case. Runs the real app on a random port with a throwaway database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpeakersTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void anEmailRegistersOnceRegardlessOfLetterCase() throws Exception {
        String email = "grace-" + UUID.randomUUID() + "@example.com";
        assertEquals(201, register("Grace Hopper", email).statusCode());
        assertEquals(409, register("Grace Hopper", email.toUpperCase()).statusCode());
    }

    @Test
    void concurrentRegistrationsOfOneEmailHaveOneWinner() throws Exception {
        String email = "racer-" + UUID.randomUUID() + "@example.com";
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String variant = i % 2 == 0 ? email : email.toUpperCase();
            sent.add(http.sendAsync(request("POST", "/speakers", speaker("Racer " + i, variant)),
                    HttpResponse.BodyHandlers.ofString()));
        }
        int created = 0;
        for (CompletableFuture<HttpResponse<String>> f : sent) {
            HttpResponse<String> res = f.join();
            assertTrue(res.statusCode() == 201 || res.statusCode() == 409, res.statusCode() + " " + res.body());
            if (res.statusCode() == 201) {
                created++;
            }
        }
        assertEquals(1, created);
    }

    @Test
    void searchMatchesNamesRegardlessOfCase() throws Exception {
        String name = "Lovelace" + UUID.randomUUID().toString().substring(0, 8);
        assertEquals(201, register("Ada " + name, "ada-" + UUID.randomUUID() + "@example.com").statusCode());

        HttpResponse<String> found = send("GET", "/speakers/search?q=" + name.toLowerCase());
        assertEquals(200, found.statusCode());
        assertTrue(found.body().contains("\"name\":\"Ada " + name + "\""), found.body());
    }

    // ---------------------------------------------------------------- spec: round trip

    @Test
    void aSpeakerReadsBackExactlyAsRegistered() throws Exception {
        String email = "Ada.Lovelace-" + UUID.randomUUID() + "@Example.COM";
        HttpResponse<String> created = post("/speakers",
                "{\"name\":\"Ådne Øst\",\"email\":\"" + email + "\",\"bio\":\"Første linje\\nandre\"}");
        assertEquals(201, created.statusCode(), created.body());
        long id = id(created);
        String expected = "{\"id\":" + id + ",\"name\":\"Ådne Øst\",\"email\":\"" + email
                + "\",\"bio\":\"Første linje\\nandre\"}";
        assertEquals(expected, created.body());
        assertEquals(expected, send("GET", "/speakers/" + id).body());
    }

    @Test
    void theBioIsOptionalAndAnEmptyBioRoundTrips() throws Exception {
        HttpResponse<String> none = register("No Bio", uniqueEmail());
        assertEquals(201, none.statusCode(), none.body());
        assertTrue(send("GET", "/speakers/" + id(none)).body().contains("\"bio\":null"));

        HttpResponse<String> empty = post("/speakers", "{\"name\":\"Empty\",\"email\":\"" + uniqueEmail() + "\",\"bio\":\"\"}");
        assertEquals(201, empty.statusCode(), empty.body());
        assertTrue(send("GET", "/speakers/" + id(empty)).body().contains("\"bio\":\"\""));
    }

    // ---------------------------------------------------------------- spec: a real name and a real email

    @Test
    void aNameIsRequired() throws Exception {
        assertEquals(400, post("/speakers", "{\"email\":\"" + uniqueEmail() + "\"}").statusCode());
        assertEquals(400, register("", uniqueEmail()).statusCode());
        assertEquals(400, register("   ", uniqueEmail()).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-an-email", "a@b", "@example.com", "ada@", "ada@example.", " ada@example.com",
            "ada@example.com ", "a da@example.com", "ada@@example.com", "ada@exa mple.com"})
    void anInvalidEmailIsRejected(String email) throws Exception {
        String unique = email.replace("ada", "ada" + UUID.randomUUID().toString().substring(0, 8));
        HttpResponse<String> res = register("Ada", unique);
        assertEquals(400, res.statusCode(), "'" + unique + "': " + res.body());
    }

    @Test
    void aMissingEmailIsRejected() throws Exception {
        assertEquals(400, post("/speakers", "{\"name\":\"Ada\"}").statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ada@example.com", "Ada.Lovelace+cfp@Mail.Example.NO", "o'brien@example.ie", "x_y-z@sub.example.co.uk"})
    void aValidEmailIsAccepted(String email) throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8) + email;
        HttpResponse<String> res = register("Ada", unique);
        assertEquals(201, res.statusCode(), unique + ": " + res.body());
    }

    // ---------------------------------------------------------------- spec: bio at most 500 characters

    @Test
    void aBioOf500CharactersIsAcceptedAnd501IsNot() throws Exception {
        assertEquals(201, registerWithBio(repeat("a", 500)).statusCode());
        assertEquals(400, registerWithBio(repeat("a", 501)).statusCode());
    }

    @Test
    void anEmojiCountsAsOneCharacter() throws Exception {
        HttpResponse<String> ok = registerWithBio(repeat("\uD83D\uDE00", 500));
        assertEquals(201, ok.statusCode(), ok.body());
        assertEquals(400, registerWithBio(repeat("\uD83D\uDE00", 501)).statusCode());
    }

    // ---------------------------------------------------------------- spec: one email is one speaker

    @Test
    void aDuplicateLeavesTheFirstSpeakerUnchanged() throws Exception {
        String email = uniqueEmail();
        HttpResponse<String> first = register("First", email);
        assertEquals(201, first.statusCode());
        assertEquals(409, register("Second", email.toUpperCase()).statusCode());
        HttpResponse<String> read = send("GET", "/speakers/" + id(first));
        assertTrue(read.body().contains("\"name\":\"First\"") && read.body().contains(email), read.body());
    }

    // ---------------------------------------------------------------- lookups and trailing slashes

    @Test
    void unknownAndMalformedIds() throws Exception {
        assertEquals(404, send("GET", "/speakers/999999999").statusCode());
        assertEquals(404, send("GET", "/speakers/999999999/").statusCode());
        assertEquals(400, send("GET", "/speakers/abc").statusCode());
    }

    @Test
    void trailingSlashesWorkLikeTheUrlsWithout() throws Exception {
        String email = uniqueEmail();
        HttpResponse<String> created = post("/speakers/", "{\"name\":\"Slash\",\"email\":\"" + email + "\"}");
        assertEquals(201, created.statusCode(), created.body());
        HttpResponse<String> read = send("GET", "/speakers/" + id(created) + "/");
        assertEquals(200, read.statusCode());
        assertEquals(send("GET", "/speakers/" + id(created)).body(), read.body());
        assertEquals(409, post("/speakers/", "{\"name\":\"Slash\",\"email\":\"" + email + "\"}").statusCode());
        assertEquals(400, post("/speakers/", "{\"name\":\"Slash\",\"email\":\"nope\"}").statusCode());
    }

    // ---------------------------------------------------------------- admin search and update

    @ParameterizedTest
    @ValueSource(strings = {"x' OR '1'='1", "'; DROP TABLE speaker;--", "%"})
    void searchTreatsTheQueryAsText(String q) throws Exception {
        assertEquals(201, register("Someone Else", uniqueEmail()).statusCode());
        HttpResponse<String> res = send("GET", "/speakers/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
        assertEquals(200, res.statusCode(), res.body());
        if (!q.equals("%")) {
            assertEquals("[]", res.body());
        }
        assertEquals(201, register("Still Here", uniqueEmail()).statusCode());
    }

    @Test
    void anUpdateFollowsTheRegistrationRules() throws Exception {
        String email = uniqueEmail();
        long id = id(register("Typo", email));
        assertEquals(400, put("/speakers/" + id, "{\"email\":\"" + email + "\"}").statusCode());
        assertEquals(400, put("/speakers/" + id, "{\"name\":\"Fixed\"}").statusCode());
        assertEquals(400, put("/speakers/" + id, "{\"name\":\"Fixed\",\"email\":\"" + email + "\",\"bio\":\""
                + repeat("a", 501) + "\"}").statusCode());
        HttpResponse<String> ok = put("/speakers/" + id, "{\"name\":\"Fixed\",\"email\":\"" + email + "\",\"bio\":\""
                + repeat("a", 500) + "\"}");
        assertEquals(200, ok.statusCode(), ok.body());
    }

    // ---------------------------------------------------------------- helpers

    private static String uniqueEmail() {
        return "s-" + UUID.randomUUID() + "@example.com";
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            b.append(s);
        }
        return b.toString();
    }

    private static long id(HttpResponse<String> res) {
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(res.body());
        assertTrue(m.find(), res.body());
        return Long.parseLong(m.group(1));
    }

    private HttpResponse<String> registerWithBio(String bio) throws Exception {
        return post("/speakers", "{\"name\":\"Bio\",\"email\":\"" + uniqueEmail() + "\",\"bio\":\"" + bio + "\"}");
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return http.send(request("POST", path, json), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String json) throws Exception {
        return http.send(request("PUT", path, json), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> register(String name, String email) throws Exception {
        return http.send(request("POST", "/speakers", speaker(name, email)), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        return http.send(request(method, path, null), HttpResponse.BodyHandlers.ofString());
    }

    private static String speaker(String name, String email) {
        return "{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}";
    }

    private HttpRequest request(String method, String path, String json) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build();
    }
}
