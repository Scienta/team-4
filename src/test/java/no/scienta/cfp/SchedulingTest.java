package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Programme scheduling (workflow/specs/scheduling.md). Real app, throwaway database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SchedulingTest {

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

    // Each test uses its own slot or room where it matters: the programme is shared by the whole class.

    @Test
    void putGetDelete() throws Exception {
        String talk = accepted("PRESENTATION", api.newSpeaker());
        HttpResponse<String> res = put("R1", "S2", talk);
        assertEquals(200, res.statusCode(), res.body());
        assertEquals("{\"room\":\"R1\",\"slot\":\"S2\",\"start\":\"10:15\",\"end\":\"11:15\",\"talkId\":" + talk + "}", res.body());

        HttpResponse<String> got = api.send("GET", "/programme/R1/S2");
        assertEquals(200, got.statusCode());
        assertEquals(res.body(), got.body());

        // same talk, same place again: unchanged
        assertEquals(200, put("R1", "S2", talk).statusCode());

        HttpResponse<String> deleted = api.send("DELETE", "/programme/R1/S2");
        assertEquals(204, deleted.statusCode(), deleted.body());
        assertEquals(404, api.send("GET", "/programme/R1/S2").statusCode());
        assertEquals(404, api.send("DELETE", "/programme/R1/S2").statusCode());
    }

    @Test
    void moveIsDeleteThenPut() throws Exception {
        String talk = accepted("LIGHTNING_10", api.newSpeaker());
        assertEquals(200, put("R3", "S1", talk).statusCode());
        assertEquals(409, put("R3", "S3", talk).statusCode());
        assertEquals(204, api.send("DELETE", "/programme/R3/S1").statusCode());
        assertEquals(200, put("R3", "S3", talk).statusCode());
        api.send("DELETE", "/programme/R3/S3");
    }

    @Test
    void unknownRoomOrSlotIs404() throws Exception {
        String talk = accepted("PRESENTATION", api.newSpeaker());
        assertEquals(404, put("R4", "S1", talk).statusCode());
        assertEquals(404, put("R1", "S6", talk).statusCode());
        assertEquals(404, put("r1", "S1", talk).statusCode());
        assertEquals(404, api.send("GET", "/programme/R0/S1").statusCode());
        assertEquals(404, api.send("DELETE", "/programme/R1/S9").statusCode());
    }

    @Test
    void badBodyOrUnknownTalkIs400() throws Exception {
        assertEquals(400, api.send("PUT", "/programme/R2/S5", "{}").statusCode());
        assertEquals(400, api.send("PUT", "/programme/R2/S5", "{\"talkId\":\"x\"}").statusCode());
        assertEquals(400, api.send("PUT", "/programme/R2/S5").statusCode());
        assertEquals(400, put("R2", "S5", "999999").statusCode());
        assertEquals(404, api.send("GET", "/programme/R2/S5").statusCode());
    }

    @Test
    void onlyAcceptedTalks() throws Exception {
        String speaker = api.newSpeaker();
        String submitted = api.newTalk(speaker);
        assertEquals(409, put("R2", "S2", submitted).statusCode());
        String waitlisted = api.newTalk(speaker);
        decide(waitlisted, "WAITLISTED");
        assertEquals(409, put("R2", "S2", waitlisted).statusCode());
        assertEquals(404, api.send("GET", "/programme/R2/S2").statusCode());
    }

    @Test
    void workshopNeedsTheLongSlot() throws Exception {
        String workshop = accepted("WORKSHOP", api.newSpeaker());
        assertEquals(409, put("R2", "S1", workshop).statusCode());
        HttpResponse<String> res = put("R2", "S4", workshop);
        assertEquals(200, res.statusCode(), res.body());
        assertTrue(res.body().contains("\"start\":\"13:30\",\"end\":\"15:30\""), res.body());
        api.send("DELETE", "/programme/R2/S4");
    }

    @Test
    void oneTalkPerRoomAndSlot() throws Exception {
        String a = accepted("PRESENTATION", api.newSpeaker());
        String b = accepted("PRESENTATION", api.newSpeaker());
        assertEquals(200, put("R1", "S3", a).statusCode());
        assertEquals(409, put("R1", "S3", b).statusCode());
        assertTrue(api.send("GET", "/programme/R1/S3").body().contains("\"talkId\":" + a));
        api.send("DELETE", "/programme/R1/S3");
    }

    @Test
    void nobodyInTwoRoomsAtOnce() throws Exception {
        String shared = api.newSpeaker();
        String a = accepted("PRESENTATION", shared);
        String b = acceptedWithCoSpeaker("LIGHTNING_20", api.newSpeaker(), shared);
        String c = accepted("PRESENTATION", api.newSpeaker());
        assertEquals(200, put("R1", "S5", a).statusCode());
        assertEquals(409, put("R2", "S5", b).statusCode(), "co-speaker of b is main speaker of a");
        assertEquals(200, put("R2", "S5", c).statusCode());
        assertEquals(200, put("R3", "S1", b).statusCode(), "another slot is fine");
        for (String place : new String[] {"R1/S5", "R2/S5", "R3/S1"}) {
            api.send("DELETE", "/programme/" + place);
        }
    }

    @Test
    void programmeStaysWhenTheTalkIsWithdrawn() throws Exception {
        String speaker = api.newSpeaker();
        String talk = accepted("PRESENTATION", speaker);
        assertEquals(200, put("R3", "S2", talk).statusCode());
        assertEquals(200, api.send("POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":" + speaker + "}").statusCode());
        assertTrue(api.send("GET", "/programme/R3/S2").body().contains("\"talkId\":" + talk));
        api.send("DELETE", "/programme/R3/S2");
    }

    // ------------------------------------------------------------ concurrency

    @Test
    void concurrentPutsIntoOneRoomAndSlot() throws Exception {
        List<String> talks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            talks.add(accepted("PRESENTATION", api.newSpeaker()));
        }
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (String t : talks) {
            sent.add(api.sendAsync("PUT", "/programme/R3/S4", "{\"talkId\":" + t + "}"));
        }
        assertEquals(1, oks(sent));
        api.send("DELETE", "/programme/R3/S4");
    }

    @Test
    void concurrentPutsOfOneTalkIntoEveryRoom() throws Exception {
        String talk = accepted("LIGHTNING_10", api.newSpeaker());
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (String room : new String[] {"R1", "R2", "R3"}) {
            for (String slot : new String[] {"S1", "S2"}) {
                sent.add(api.sendAsync("PUT", "/programme/" + room + "/" + slot, "{\"talkId\":" + talk + "}"));
            }
        }
        assertEquals(1, oks(sent));
        for (String room : new String[] {"R1", "R2", "R3"}) {
            for (String slot : new String[] {"S1", "S2"}) {
                api.send("DELETE", "/programme/" + room + "/" + slot);
            }
        }
    }

    @Test
    void concurrentPutsOfTalksSharingASpeaker() throws Exception {
        for (int round = 0; round < 3; round++) {
            String shared = api.newSpeaker();
            String a = accepted("PRESENTATION", shared);
            String b = acceptedWithCoSpeaker("PRESENTATION", api.newSpeaker(), shared);
            List<CompletableFuture<HttpResponse<String>>> sent = List.of(
                    api.sendAsync("PUT", "/programme/R1/S3", "{\"talkId\":" + a + "}"),
                    api.sendAsync("PUT", "/programme/R2/S3", "{\"talkId\":" + b + "}"));
            assertEquals(1, oks(sent), "round " + round);
            api.send("DELETE", "/programme/R1/S3");
            api.send("DELETE", "/programme/R2/S3");
        }
    }

    // ------------------------------------------------------------ helpers

    private static int oks(List<CompletableFuture<HttpResponse<String>>> sent) {
        int ok = 0;
        for (CompletableFuture<HttpResponse<String>> f : sent) {
            HttpResponse<String> res = f.join();
            assertTrue(res.statusCode() == 200 || res.statusCode() == 409, res.statusCode() + " " + res.body());
            ok += res.statusCode() == 200 ? 1 : 0;
        }
        return ok;
    }

    private String accepted(String format, String speaker) throws Exception {
        HttpResponse<String> res = api.send("POST", "/talks",
                "{\"title\":\"T\",\"format\":\"" + format + "\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}");
        assertEquals(201, res.statusCode(), res.body());
        String talk = TestApi.firstId(res.body());
        assertEquals(200, api.send("PUT", "/talks/" + talk + "/ratings/1", "{\"score\":4}").statusCode());
        assertEquals(200, api.send("PUT", "/talks/" + talk + "/ratings/2", "{\"score\":4}").statusCode());
        assertEquals(200, decide(talk, "ACCEPTED").statusCode());
        return talk;
    }

    private String acceptedWithCoSpeaker(String format, String speaker, String coSpeaker) throws Exception {
        String talk = accepted(format, speaker);
        assertEquals(200, api.send("PUT", "/talks/" + talk + "/co-speakers/" + coSpeaker).statusCode());
        return talk;
    }

    private HttpResponse<String> decide(String talk, String status) throws Exception {
        return api.send("PUT", "/talks/" + talk + "/status", "{\"status\":\"" + status + "\"}");
    }

    private HttpResponse<String> put(String room, String slot, String talk) throws Exception {
        return api.send("PUT", "/programme/" + room + "/" + slot, "{\"talkId\":" + talk + "}");
    }
}
