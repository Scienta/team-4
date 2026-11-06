package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A speaker submits at most 3 talks, also when the submissions arrive at the same time. Runs the real app on a random
 * port with a throwaway database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TalksTest {

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
    void concurrentSubmissionsNeverExceedThreeTalks() throws Exception {
        String speaker = api.newSpeaker();
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            sent.add(api.sendAsync("POST", "/talks", TestApi.talk("Talk " + i, speaker)));
        }
        int created = 0;
        for (CompletableFuture<HttpResponse<String>> f : sent) {
            HttpResponse<String> res = f.join();
            assertTrue(res.statusCode() == 201 || res.statusCode() == 409, res.statusCode() + " " + res.body());
            if (res.statusCode() == 201) {
                created++;
            }
        }
        assertEquals(3, created);
        HttpResponse<String> talks = api.send("GET", "/speakers/" + speaker + "/talks");
        assertEquals(3, TestApi.countIds(talks.body()));
    }

    @Test
    void aTalkHasItsSubmissionTimeAndPlainSubmissionDate() throws Exception {
        HttpResponse<String> res = api.send("POST", "/talks", TestApi.talk("Dates", api.newSpeaker()));
        assertEquals(201, res.statusCode(), res.body());
        assertTrue(Pattern.compile("\"submittedAt\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+00:00\"")
                .matcher(res.body()).find(), res.body());
        assertTrue(Pattern.compile("\"submittedOn\":\"\\d{4}-\\d{2}-\\d{2}\"").matcher(res.body()).find(), res.body());
    }
}
