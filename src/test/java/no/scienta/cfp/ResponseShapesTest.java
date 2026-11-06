package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Pins the exact JSON of the speaker and rating responses (field names, order, nulls) and how the hand-checked request
 * bodies treat values Jackson would otherwise coerce. Clients depend on both. Real app, throwaway database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResponseShapesTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(port);
    }

    // ---------------------------------------------------------------- speakers

    @Test
    void registerAndGetHaveIdNameEmailBioInThatOrder() throws Exception {
        String email = uniqueEmail();
        HttpResponse<String> created = api.send("POST", "/speakers", "{\"bio\":null,\"email\":\"" + email + "\",\"name\":\"Ada\"}");
        assertEquals(201, created.statusCode(), created.body());
        String id = TestApi.firstId(created.body());
        String expected = "{\"id\":" + id + ",\"name\":\"Ada\",\"email\":\"" + email + "\",\"bio\":null}";
        assertEquals(expected, created.body());
        assertEquals(expected, api.send("GET", "/speakers/" + id).body());
    }

    @Test
    void updateReturnsTheSpeakerInTheSameShape() throws Exception {
        String id = api.newSpeaker();
        String email = uniqueEmail();
        HttpResponse<String> res = api.send("PUT", "/speakers/" + id,
                "{\"bio\":\"Hi\",\"email\":\"" + email + "\",\"name\":\"Fixed\",\"extra\":1}");
        assertEquals(200, res.statusCode(), res.body());
        assertEquals("{\"id\":" + id + ",\"name\":\"Fixed\",\"email\":\"" + email + "\",\"bio\":\"Hi\"}", res.body());
    }

    @Test
    void updateTakesScalarsAsText() throws Exception {
        String id = api.newSpeaker();
        String email = uniqueEmail();
        HttpResponse<String> res = api.send("PUT", "/speakers/" + id,
                "{\"name\":5,\"email\":\"" + email + "\",\"bio\":true}");
        assertEquals(200, res.statusCode(), res.body());
        assertEquals("{\"id\":" + id + ",\"name\":\"5\",\"email\":\"" + email + "\",\"bio\":\"true\"}", res.body());
    }

    @Test
    void updateErrors() throws Exception {
        String id = api.newSpeaker();
        assertMessage(400, "name is required", api.send("PUT", "/speakers/" + id, "{\"email\":\"" + uniqueEmail() + "\"}"));
        assertMessage(400, "invalid email", api.send("PUT", "/speakers/" + id, "{\"name\":\"X\"}"));
        assertMessage(404, "speaker not found", api.send("PUT", "/speakers/999999999", "{\"name\":\"X\",\"email\":\"" + uniqueEmail() + "\"}"));
        assertEquals(400, api.send("PUT", "/speakers/" + id).statusCode());
        assertEquals(400, api.send("PUT", "/speakers/" + id, "{\"name\":{\"a\":1},\"email\":\"" + uniqueEmail() + "\"}").statusCode());
    }

    @Test
    void searchListsSpeakersInTheSameShape() throws Exception {
        String name = "Shape" + UUID.randomUUID().toString().substring(0, 8);
        String email = uniqueEmail();
        HttpResponse<String> created = api.send("POST", "/speakers", "{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"bio\":\"b\"}");
        String id = TestApi.firstId(created.body());
        HttpResponse<String> res = api.send("GET", "/speakers/search?q=" + name);
        assertEquals(200, res.statusCode(), res.body());
        assertEquals("[{\"id\":" + id + ",\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"bio\":\"b\"}]", res.body());
    }

    @Test
    void csvExportNamesTheSpeaker() throws Exception {
        String name = "Csv" + UUID.randomUUID().toString().substring(0, 8);
        HttpResponse<String> created = api.send("POST", "/speakers", "{\"name\":\"" + name + "\",\"email\":\"" + uniqueEmail() + "\"}");
        String talk = api.newTalk(TestApi.firstId(created.body()));
        HttpResponse<String> csv = api.send("GET", "/admin/talks.csv");
        assertEquals(200, csv.statusCode());
        assertTrue(csv.body().contains("\n" + talk + ";Talk;Presentation;45;Beginner;" + name + ";SUBMITTED;"), csv.body());
    }

    // ---------------------------------------------------------------- ratings

    @Test
    void ratingResponseHasTalkReviewerScoreAndDisplayInThatOrder() throws Exception {
        String talk = api.newTalk(api.newSpeaker());
        HttpResponse<String> res = api.send("PUT", "/talks/" + talk + "/ratings/7", "{\"score\":4}");
        assertEquals(200, res.statusCode(), res.body());
        assertTrue(res.body().matches("\\{\"talkId\":" + talk + ",\"reviewerId\":7,\"score\":4,"
                + "\"ratedAtDisplay\":\"\\d{1,2}/\\d{1,2}/\\d{2}, \\d{1,2}:\\d{2} [AP]M\"}"), res.body());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"4\"", "4.0", "4.5", "null", "true", "[4]", "3000000000"})
    void aScoreJacksonCouldCoerceIsStillRejected(String score) throws Exception {
        String talk = api.newTalk(api.newSpeaker());
        assertMessage(400, "score must be a whole number",
                api.send("PUT", "/talks/" + talk + "/ratings/1", "{\"score\":" + score + "}"));
        assertTrue(api.send("GET", "/talks/" + talk).body().contains("\"ratingCount\":0"));
    }

    @Test
    void aMissingScoreIsRejected() throws Exception {
        String talk = api.newTalk(api.newSpeaker());
        assertMessage(400, "score must be a whole number", api.send("PUT", "/talks/" + talk + "/ratings/1", "{}"));
        assertEquals(400, api.send("PUT", "/talks/" + talk + "/ratings/1").statusCode());
    }

    // ---------------------------------------------------------------- acceptance and workshops

    @ParameterizedTest
    @ValueSource(strings = {"\"5\"", "5.0", "null", "[5]", "true"})
    void aSpeakerIdJacksonCouldCoerceIsStillRejected(String speakerId) throws Exception {
        String talk = api.newTalk(api.newSpeaker());
        assertMessage(400, "speakerId must be a whole number",
                api.send("POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":" + speakerId + "}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[\"ACCEPTED\"]", "1", "\"accepted\""})
    void aStatusThatIsNotADecisionIsRejected(String status) throws Exception {
        String talk = api.newTalk(api.newSpeaker());
        assertMessage(400, "status must be one of ACCEPTED, REJECTED, WAITLISTED",
                api.send("PUT", "/talks/" + talk + "/status", "{\"status\":" + status + "}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3", "true", "[\"Ada\"]"})
    void aWorkshopNameThatIsNotTextIsRejected(String name) throws Exception {
        String talk = api.newTalk(api.newSpeaker());
        assertMessage(400, "name is required", api.send("POST", "/workshops/" + talk + "/registrations",
                "{\"name\":" + name + ",\"email\":\"a@example.com\"}"));
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The status, and the error body Spring renders for it. {@code message} is the service's reason, kept here as
     * documentation: the error body doesn't include it as configured, so a binding failure (Jackson) and a refusal
     * by the service look alike; what tells them apart is whether a coercible value gets through at all.
     */
    private static void assertMessage(int status, String message, HttpResponse<String> res) {
        assertEquals(status, res.statusCode(), message + ": " + res.body());
        assertTrue(res.body().matches("\\{\"timestamp\":\"[^\"]+\",\"status\":" + status + ",\"error\":\"[^\"]+\",\"path\":\"[^\"]+\"}"),
                res.body());
    }

    private static String uniqueEmail() {
        return "shape-" + UUID.randomUUID() + "@example.com";
    }
}
