package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Co-speakers on a talk (workflow/specs/co-speakers.md). Runs the real app on a random port with a throwaway database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CoSpeakersTest {

    private static final Pattern CO = Pattern.compile("\"coSpeakerIds\":\\[([^\\]]*)\\]");

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

    @Test
    void newTalkHasNoCoSpeakers() throws Exception {
        HttpResponse<String> created = api.send("POST", "/talks", TestApi.talk("Talk", newSpeaker()));
        assertEquals(201, created.statusCode(), created.body());
        assertEquals("", coSpeakers(created.body()));
        HttpResponse<String> res = send("GET", "/talks/" + TestApi.firstId(created.body()));
        assertEquals(200, res.statusCode());
        assertEquals("", coSpeakers(res.body()));
    }

    @Test
    void addsCoSpeakersInOrderAndIgnoresDuplicates() throws Exception {
        String talk = newTalk(newSpeaker());
        String a = newSpeaker();
        String b = newSpeaker();

        HttpResponse<String> res = send("PUT", "/talks/" + talk + "/co-speakers/" + b);
        assertEquals(200, res.statusCode(), res.body());
        assertEquals(b, coSpeakers(res.body()));

        res = send("PUT", "/talks/" + talk + "/co-speakers/" + a);
        assertEquals(200, res.statusCode(), res.body());
        assertEquals(b + "," + a, coSpeakers(res.body()));

        res = send("PUT", "/talks/" + talk + "/co-speakers/" + b);
        assertEquals(200, res.statusCode(), res.body());
        assertEquals(b + "," + a, coSpeakers(res.body()));

        assertEquals(b + "," + a, coSpeakers(send("GET", "/talks/" + talk).body()));
        assertTrue(send("GET", "/talks").body().contains("\"coSpeakerIds\":[" + b + "," + a + "]"));
    }

    @Test
    void atMostTwoCoSpeakers() throws Exception {
        String talk = newTalk(newSpeaker());
        assertEquals(200, send("PUT", "/talks/" + talk + "/co-speakers/" + newSpeaker()).statusCode());
        assertEquals(200, send("PUT", "/talks/" + talk + "/co-speakers/" + newSpeaker()).statusCode());
        assertEquals(409, send("PUT", "/talks/" + talk + "/co-speakers/" + newSpeaker()).statusCode());
    }

    @Test
    void mainSpeakerAsCoSpeakerIs400() throws Exception {
        String main = newSpeaker();
        String talk = newTalk(main);
        assertEquals(400, send("PUT", "/talks/" + talk + "/co-speakers/" + main).statusCode());
        assertEquals("", coSpeakers(send("GET", "/talks/" + talk).body()));
    }

    @Test
    void unknownTalkOrSpeakerIs404() throws Exception {
        String talk = newTalk(newSpeaker());
        String speaker = newSpeaker();
        assertEquals(404, send("PUT", "/talks/999999/co-speakers/" + speaker).statusCode());
        assertEquals(404, send("PUT", "/talks/" + talk + "/co-speakers/999999").statusCode());
        assertEquals(404, send("DELETE", "/talks/999999/co-speakers/" + speaker).statusCode());
        assertEquals(404, send("DELETE", "/talks/" + talk + "/co-speakers/999999").statusCode());
        assertEquals(400, send("PUT", "/talks/" + talk + "/co-speakers/abc").statusCode());
    }

    @Test
    void removeAndReAdd() throws Exception {
        String talk = newTalk(newSpeaker());
        String a = newSpeaker();
        String b = newSpeaker();
        send("PUT", "/talks/" + talk + "/co-speakers/" + a);
        send("PUT", "/talks/" + talk + "/co-speakers/" + b);

        HttpResponse<String> res = send("DELETE", "/talks/" + talk + "/co-speakers/" + a);
        assertEquals(200, res.statusCode(), res.body());
        assertEquals(b, coSpeakers(res.body()));

        res = send("PUT", "/talks/" + talk + "/co-speakers/" + a);
        assertEquals(b + "," + a, coSpeakers(res.body()));
    }

    @Test
    void removingSomeoneWhoIsNotACoSpeakerIs404() throws Exception {
        String talk = newTalk(newSpeaker());
        String a = newSpeaker();
        send("PUT", "/talks/" + talk + "/co-speakers/" + a);
        send("DELETE", "/talks/" + talk + "/co-speakers/" + a);

        assertEquals(404, send("DELETE", "/talks/" + talk + "/co-speakers/" + a).statusCode());
        assertEquals(404, send("DELETE", "/talks/" + talk + "/co-speakers/" + newSpeaker()).statusCode());
    }

    @Test
    void coSpeakingIsNotListedAsOwnTalks() throws Exception {
        // The cap now counts co-speaking (SubmissionCapTest); the listings still show main-speaker talks only.
        String busy = newSpeaker();
        newTalk(busy);
        newTalk(busy);
        String other = newTalk(newSpeaker());

        assertEquals(200, send("PUT", "/talks/" + other + "/co-speakers/" + busy).statusCode());
        assertEquals(2, TestApi.countIds(send("GET", "/speakers/" + busy + "/talks").body()));
        assertFalse(send("GET", "/talks?speakerId=" + busy).body().contains("\"id\":" + other + ","));
    }

    @Test
    void concurrentAddsNeverExceedTwo() throws Exception {
        String talk = newTalk(newSpeaker());
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String s = newSpeaker();
            sent.add(api.sendAsync("PUT", "/talks/" + talk + "/co-speakers/" + s, null));
        }
        int ok = 0;
        for (CompletableFuture<HttpResponse<String>> f : sent) {
            HttpResponse<String> res = f.join();
            assertTrue(res.statusCode() == 200 || res.statusCode() == 409, res.statusCode() + " " + res.body());
            if (res.statusCode() == 200) {
                ok++;
            }
        }
        assertEquals(2, ok);
        assertEquals(2, coSpeakers(send("GET", "/talks/" + talk).body()).split(",").length);
    }

    private String newSpeaker() throws Exception {
        return api.newSpeaker();
    }

    private String newTalk(String speaker) throws Exception {
        return api.newTalk(speaker);
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        return api.send(method, path);
    }

    private static String coSpeakers(String body) {
        Matcher m = CO.matcher(body);
        assertTrue(m.find(), "no coSpeakerIds in " + body);
        return m.group(1);
    }
}
