package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
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
 * Accept, reject, waitlist and withdraw talks (workflow/specs/acceptance.md). Real app, throwaway database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AcceptanceTest {

    private static final Pattern STATUS = Pattern.compile("\"status\":\"([A-Z]+)\"");

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

    // ------------------------------------------------------------ committee decisions

    @Test
    void newTalkIsSubmitted() throws Exception {
        assertEquals("SUBMITTED", statusOf(newTalk()));
    }

    @Test
    void acceptsATalkRatedByTwoReviewers() throws Exception {
        String talk = newTalk();
        rate(talk, 1);
        rate(talk, 2);
        HttpResponse<String> res = decide(talk, "ACCEPTED");
        assertEquals(200, res.statusCode(), res.body());
        assertEquals("ACCEPTED", status(res.body()));
        assertEquals(talk, TestApi.firstId(res.body()));
        assertEquals("ACCEPTED", statusOf(talk));
    }

    @Test
    void acceptingNeedsTwoDistinctReviewers() throws Exception {
        String talk = newTalk();
        assertEquals(409, decide(talk, "ACCEPTED").statusCode());
        rate(talk, 7);
        rate(talk, 7);
        assertEquals(409, decide(talk, "ACCEPTED").statusCode());
        assertEquals("SUBMITTED", statusOf(talk));
        rate(talk, 8);
        assertEquals(200, decide(talk, "ACCEPTED").statusCode());
    }

    @Test
    void rejectAndWaitlistNeedNoRatings() throws Exception {
        String a = newTalk();
        assertEquals(200, decide(a, "REJECTED").statusCode());
        assertEquals("REJECTED", statusOf(a));
        String b = newTalk();
        assertEquals(200, decide(b, "WAITLISTED").statusCode());
        assertEquals("WAITLISTED", statusOf(b));
    }

    @Test
    void waitlistedCanStillBeAcceptedOrRejected() throws Exception {
        String a = waitlisted();
        rate(a, 1);
        rate(a, 2);
        assertEquals(200, decide(a, "ACCEPTED").statusCode());
        String b = waitlisted();
        assertEquals(200, decide(b, "REJECTED").statusCode());
        String c = waitlisted();
        assertEquals(409, decide(c, "WAITLISTED").statusCode());
        assertEquals("WAITLISTED", statusOf(c));
    }

    @Test
    void acceptedAndRejectedAreFinal() throws Exception {
        String accepted = newTalk();
        rate(accepted, 1);
        rate(accepted, 2);
        decide(accepted, "ACCEPTED");
        for (String s : new String[] {"ACCEPTED", "REJECTED", "WAITLISTED"}) {
            assertEquals(409, decide(accepted, s).statusCode(), s);
        }
        assertEquals("ACCEPTED", statusOf(accepted));

        String rejected = newTalk();
        rate(rejected, 1);
        rate(rejected, 2);
        decide(rejected, "REJECTED");
        for (String s : new String[] {"ACCEPTED", "REJECTED", "WAITLISTED"}) {
            assertEquals(409, decide(rejected, s).statusCode(), s);
        }
        assertEquals("REJECTED", statusOf(rejected));
    }

    @Test
    void badDecisionsAre400() throws Exception {
        String talk = newTalk();
        assertEquals(400, decide(talk, "SUBMITTED").statusCode());
        assertEquals(400, decide(talk, "WITHDRAWN").statusCode());
        assertEquals(400, decide(talk, "MAYBE").statusCode());
        assertEquals(400, api.send("PUT", "/talks/" + talk + "/status", "{}").statusCode());
        assertEquals(400, api.send("PUT", "/talks/" + talk + "/status", "{\"status\":3}").statusCode());
        assertEquals(400, api.send("PUT", "/talks/" + talk + "/status").statusCode());
        assertEquals("SUBMITTED", statusOf(talk));
    }

    @Test
    void unknownTalkIs404() throws Exception {
        assertEquals(404, decide("999999", "REJECTED").statusCode());
        assertEquals(404, withdraw("999999", newSpeaker()).statusCode());
    }

    // ------------------------------------------------------------ withdrawal

    @Test
    void speakerWithdrawsFromSubmittedWaitlistedOrAccepted() throws Exception {
        String speaker = newSpeaker();
        String submitted = api.newTalk(speaker);
        HttpResponse<String> res = withdraw(submitted, speaker);
        assertEquals(200, res.statusCode(), res.body());
        assertEquals("WITHDRAWN", status(res.body()));
        assertEquals("WITHDRAWN", statusOf(submitted));

        String wait = api.newTalk(speaker);
        decide(wait, "WAITLISTED");
        assertEquals(200, withdraw(wait, speaker).statusCode());

        String accepted = api.newTalk(speaker);
        rate(accepted, 1);
        rate(accepted, 2);
        decide(accepted, "ACCEPTED");
        assertEquals(200, withdraw(accepted, speaker).statusCode());
        assertEquals("WITHDRAWN", statusOf(accepted));
    }

    @Test
    void cannotWithdrawRejectedOrWithdrawn() throws Exception {
        String speaker = newSpeaker();
        String rejected = api.newTalk(speaker);
        decide(rejected, "REJECTED");
        assertEquals(409, withdraw(rejected, speaker).statusCode());
        assertEquals("REJECTED", statusOf(rejected));

        String gone = api.newTalk(speaker);
        withdraw(gone, speaker);
        assertEquals(409, withdraw(gone, speaker).statusCode());
    }

    @Test
    void withdrawnIsOutForGood() throws Exception {
        String speaker = newSpeaker();
        String talk = api.newTalk(speaker);
        rate(talk, 1);
        rate(talk, 2);
        withdraw(talk, speaker);
        for (String s : new String[] {"ACCEPTED", "REJECTED", "WAITLISTED"}) {
            assertEquals(409, decide(talk, s).statusCode(), s);
        }
        assertEquals("WITHDRAWN", statusOf(talk));
    }

    @Test
    void onlyTheMainSpeakerCanWithdraw() throws Exception {
        String talk = newTalk();
        String co = newSpeaker();
        assertEquals(200, api.send("PUT", "/talks/" + talk + "/co-speakers/" + co).statusCode());
        assertEquals(403, withdraw(talk, newSpeaker()).statusCode());
        assertEquals(403, withdraw(talk, co).statusCode());
        assertEquals(403, withdraw(talk, "999999").statusCode());
        assertEquals("SUBMITTED", statusOf(talk));
    }

    @Test
    void badWithdrawalsAre400() throws Exception {
        String talk = newTalk();
        assertEquals(400, api.send("POST", "/talks/" + talk + "/withdraw", "{}").statusCode());
        assertEquals(400, api.send("POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":\"x\"}").statusCode());
        assertEquals(400, api.send("POST", "/talks/" + talk + "/withdraw").statusCode());
    }

    // ------------------------------------------------------------ visibility and concurrency

    @Test
    void statusShowsInEveryTalkList() throws Exception {
        String speaker = newSpeaker();
        String talk = api.newTalk(speaker);
        decide(talk, "WAITLISTED");
        String marker = "\"id\":" + talk + ",";
        assertTrue(segment(api.send("GET", "/talks").body(), marker).contains("\"status\":\"WAITLISTED\""));
        assertTrue(segment(api.send("GET", "/speakers/" + speaker + "/talks").body(), marker).contains("\"status\":\"WAITLISTED\""));
        assertTrue(segment(api.send("GET", "/talks?speakerId=" + speaker).body(), marker).contains("\"status\":\"WAITLISTED\""));
    }

    @Test
    void concurrentDecisionsLeaveALegalState() throws Exception {
        for (int round = 0; round < 5; round++) {
            String talk = newTalk();
            rate(talk, 1);
            rate(talk, 2);
            CompletableFuture<HttpResponse<String>> accept = api.sendAsync("PUT", "/talks/" + talk + "/status", "{\"status\":\"ACCEPTED\"}");
            CompletableFuture<HttpResponse<String>> reject = api.sendAsync("PUT", "/talks/" + talk + "/status", "{\"status\":\"REJECTED\"}");
            int a = accept.join().statusCode();
            int r = reject.join().statusCode();
            assertTrue((a == 200 && r == 409) || (a == 409 && r == 200), a + " " + r);
            assertEquals(a == 200 ? "ACCEPTED" : "REJECTED", statusOf(talk));
        }
    }

    // ------------------------------------------------------------ helpers

    private String newSpeaker() throws Exception {
        return api.newSpeaker();
    }

    private String newTalk() throws Exception {
        return api.newTalk(newSpeaker());
    }

    private String waitlisted() throws Exception {
        String talk = newTalk();
        assertEquals(200, decide(talk, "WAITLISTED").statusCode());
        return talk;
    }

    private void rate(String talk, int reviewer) throws Exception {
        HttpResponse<String> res = api.send("PUT", "/talks/" + talk + "/ratings/" + reviewer, "{\"score\":4}");
        assertEquals(200, res.statusCode(), res.body());
    }

    private HttpResponse<String> decide(String talk, String status) throws Exception {
        return api.send("PUT", "/talks/" + talk + "/status", "{\"status\":\"" + status + "\"}");
    }

    private HttpResponse<String> withdraw(String talk, String speaker) throws Exception {
        return api.send("POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":" + speaker + "}");
    }

    private String statusOf(String talk) throws Exception {
        HttpResponse<String> res = api.send("GET", "/talks/" + talk);
        assertEquals(200, res.statusCode(), res.body());
        return status(res.body());
    }

    private static String status(String body) {
        Matcher m = STATUS.matcher(body);
        assertTrue(m.find(), "no status in " + body);
        return m.group(1);
    }

    /** The part of a JSON list starting at one talk's id, up to the next talk. */
    private static String segment(String body, String marker) {
        int start = body.indexOf(marker);
        assertTrue(start >= 0, marker + " not in " + body);
        int end = body.indexOf("\"id\":", start + marker.length());
        return body.substring(start, end < 0 ? body.length() : end);
    }
}
