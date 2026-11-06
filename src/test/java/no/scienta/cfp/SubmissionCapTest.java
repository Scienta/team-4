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

/**
 * Change request: submission cap (workflow/specs/submission-cap-cr.md, a guess before the spec was out): a talk
 * counts for its main speaker and every co-speaker, and rejected or withdrawn talks don't count.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SubmissionCapTest {

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
    void coSpeakingCountsAgainstSubmitting() throws Exception {
        String ada = api.newSpeaker();
        api.newTalk(ada);
        api.newTalk(ada);
        assertEquals(200, addCoSpeaker(api.newTalk(api.newSpeaker()), ada).statusCode());

        assertEquals(409, submit(ada).statusCode());
    }

    @Test
    void coSpeakingCountsAgainstBeingAddedAsCoSpeaker() throws Exception {
        String ada = api.newSpeaker();
        api.newTalk(ada);
        api.newTalk(ada);
        assertEquals(200, addCoSpeaker(api.newTalk(api.newSpeaker()), ada).statusCode());

        String fourth = api.newTalk(api.newSpeaker());
        assertEquals(409, addCoSpeaker(fourth, ada).statusCode());
        assertEquals("", coSpeakersOf(fourth));
    }

    @Test
    void mainSpeakerTalksCountAgainstBeingAddedAsCoSpeaker() throws Exception {
        String ada = api.newSpeaker();
        api.newTalk(ada);
        api.newTalk(ada);
        api.newTalk(ada);

        assertEquals(409, addCoSpeaker(api.newTalk(api.newSpeaker()), ada).statusCode());
    }

    @Test
    void rejectedTalkFreesASlot() throws Exception {
        String ada = api.newSpeaker();
        String first = api.newTalk(ada);
        api.newTalk(ada);
        api.newTalk(ada);
        assertEquals(409, submit(ada).statusCode());

        assertEquals(200, setStatus(first, "REJECTED").statusCode());
        assertEquals(201, submit(ada).statusCode());
    }

    @Test
    void withdrawnTalkFreesASlotForItsCoSpeakers() throws Exception {
        String ada = api.newSpeaker();
        api.newTalk(ada);
        api.newTalk(ada);
        String bob = api.newSpeaker();
        String bobsTalk = api.newTalk(bob);
        assertEquals(200, addCoSpeaker(bobsTalk, ada).statusCode());
        assertEquals(409, submit(ada).statusCode());

        assertEquals(200, api.send("POST", "/talks/" + bobsTalk + "/withdraw", "{\"speakerId\":" + bob + "}").statusCode());
        assertEquals(201, submit(ada).statusCode());
    }

    @Test
    void addingToARejectedTalkIsAllowedAtTheCap() throws Exception {
        String ada = api.newSpeaker();
        api.newTalk(ada);
        api.newTalk(ada);
        api.newTalk(ada);
        String rejected = api.newTalk(api.newSpeaker());
        assertEquals(200, setStatus(rejected, "REJECTED").statusCode());

        assertEquals(200, addCoSpeaker(rejected, ada).statusCode());
    }

    @Test
    void addingAnExistingCoSpeakerAgainAtTheCapChangesNothing() throws Exception {
        String ada = api.newSpeaker();
        api.newTalk(ada);
        api.newTalk(ada);
        String talk = api.newTalk(api.newSpeaker());
        assertEquals(200, addCoSpeaker(talk, ada).statusCode());

        assertEquals(200, addCoSpeaker(talk, ada).statusCode());
        assertEquals(ada, coSpeakersOf(talk));
    }

    @Test
    void concurrentSubmitsAndCoSpeakerAddsNeverPassTheCap() throws Exception {
        String ada = api.newSpeaker();
        api.newTalk(ada);
        api.newTalk(ada);
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            sent.add(api.sendAsync("POST", "/talks", TestApi.talk("Talk", ada)));
            sent.add(api.sendAsync("PUT", "/talks/" + api.newTalk(api.newSpeaker()) + "/co-speakers/" + ada, null));
        }
        int ok = 0;
        for (CompletableFuture<HttpResponse<String>> f : sent) {
            HttpResponse<String> res = f.join();
            int status = res.statusCode();
            assertTrue(status == 200 || status == 201 || status == 409, status + " " + res.body());
            if (status != 409) {
                ok++;
            }
        }
        assertEquals(1, ok);
    }

    private HttpResponse<String> submit(String speakerId) throws Exception {
        return api.send("POST", "/talks", TestApi.talk("Talk", speakerId));
    }

    private HttpResponse<String> addCoSpeaker(String talk, String speakerId) throws Exception {
        return api.send("PUT", "/talks/" + talk + "/co-speakers/" + speakerId);
    }

    private HttpResponse<String> setStatus(String talk, String status) throws Exception {
        return api.send("PUT", "/talks/" + talk + "/status", "{\"status\":\"" + status + "\"}");
    }

    private String coSpeakersOf(String talk) throws Exception {
        String body = api.send("GET", "/talks/" + talk).body();
        int start = body.indexOf("\"coSpeakerIds\":[") + "\"coSpeakerIds\":[".length();
        return body.substring(start, body.indexOf(']', start));
    }
}
