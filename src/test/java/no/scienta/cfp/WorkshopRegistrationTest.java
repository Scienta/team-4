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

/** Registering for workshops (workflow/specs/workshop-registration.md). Real app, throwaway database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkshopRegistrationTest {

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
    void registersAndListsInOrder() throws Exception {
        String ws = acceptedWorkshop();
        HttpResponse<String> res = register(ws, "Ada", "Ada@Example.com");
        assertEquals(201, res.statusCode(), res.body());
        assertTrue(res.body().contains("\"workshopId\":" + ws), res.body());
        assertTrue(res.body().contains("\"name\":\"Ada\""), res.body());
        assertTrue(res.body().contains("\"email\":\"Ada@Example.com\""), res.body());
        assertTrue(res.body().matches(".*\"id\":\\d+.*"), res.body());

        assertEquals(201, register(ws, "Bob", "bob@example.com").statusCode());
        assertEquals(201, register(ws, "Cy", "cy@example.com").statusCode());

        HttpResponse<String> list = api.send("GET", "/workshops/" + ws + "/registrations");
        assertEquals(200, list.statusCode(), list.body());
        String body = list.body();
        assertEquals(3, TestApi.countIds(body));
        assertTrue(body.indexOf("Ada@Example.com") < body.indexOf("bob@example.com"), body);
        assertTrue(body.indexOf("bob@example.com") < body.indexOf("cy@example.com"), body);
    }

    @Test
    void oneRegistrationPerEmailWhateverTheCase() throws Exception {
        String ws = acceptedWorkshop();
        assertEquals(201, register(ws, "Ada", "ada@example.com").statusCode());
        assertEquals(409, register(ws, "Ada again", "ADA@Example.com").statusCode());
        assertEquals(1, TestApi.countIds(api.send("GET", "/workshops/" + ws + "/registrations").body()));

        String other = acceptedWorkshop();
        assertEquals(201, register(other, "Ada", "ada@example.com").statusCode());
    }

    @Test
    void nameAndEmailRequired() throws Exception {
        String ws = acceptedWorkshop();
        String path = "/workshops/" + ws + "/registrations";
        assertEquals(400, api.send("POST", path, "{\"email\":\"a@example.com\"}").statusCode());
        assertEquals(400, api.send("POST", path, "{\"name\":\"  \",\"email\":\"a@example.com\"}").statusCode());
        assertEquals(400, api.send("POST", path, "{\"name\":\"Ada\"}").statusCode());
        assertEquals(400, api.send("POST", path, "{\"name\":\"Ada\",\"email\":\"not-an-email\"}").statusCode());
        assertEquals(400, api.send("POST", path, "{\"name\":3,\"email\":\"a@example.com\"}").statusCode());
        assertEquals(400, api.send("POST", path).statusCode());
        assertEquals("[]", api.send("GET", path).body());
    }

    @Test
    void unknownTalkOrNotAWorkshopIs404() throws Exception {
        assertEquals(404, register("999999", "Ada", "ada@example.com").statusCode());
        assertEquals(404, api.send("GET", "/workshops/999999/registrations").statusCode());

        String presentation = api.newTalk(api.newSpeaker());
        rateTwice(presentation);
        assertEquals(200, decide(presentation, "ACCEPTED").statusCode());
        assertEquals(404, register(presentation, "Ada", "ada@example.com").statusCode());
        assertEquals(404, api.send("GET", "/workshops/" + presentation + "/registrations").statusCode());
    }

    @Test
    void onlyAcceptedWorkshopsTakeRegistrations() throws Exception {
        String submitted = workshop(api.newSpeaker());
        assertEquals(409, register(submitted, "Ada", "ada@example.com").statusCode());
        assertEquals("[]", api.send("GET", "/workshops/" + submitted + "/registrations").body());

        String waitlisted = workshop(api.newSpeaker());
        decide(waitlisted, "WAITLISTED");
        assertEquals(409, register(waitlisted, "Ada", "ada@example.com").statusCode());

        String rejected = workshop(api.newSpeaker());
        decide(rejected, "REJECTED");
        assertEquals(409, register(rejected, "Ada", "ada@example.com").statusCode());
    }

    @Test
    void withdrawnWorkshopKeepsItsListButTakesNoMore() throws Exception {
        String speaker = api.newSpeaker();
        String ws = workshop(speaker);
        rateTwice(ws);
        decide(ws, "ACCEPTED");
        assertEquals(201, register(ws, "Ada", "ada@example.com").statusCode());
        assertEquals(200, api.send("POST", "/talks/" + ws + "/withdraw", "{\"speakerId\":" + speaker + "}").statusCode());

        assertEquals(409, register(ws, "Bob", "bob@example.com").statusCode());
        HttpResponse<String> list = api.send("GET", "/workshops/" + ws + "/registrations");
        assertEquals(200, list.statusCode());
        assertEquals(1, TestApi.countIds(list.body()));
        assertTrue(list.body().contains("ada@example.com"));
    }

    @Test
    void twentySeats() throws Exception {
        String ws = acceptedWorkshop();
        for (int i = 0; i < 20; i++) {
            assertEquals(201, register(ws, "P" + i, "p" + i + "@example.com").statusCode());
        }
        assertEquals(409, register(ws, "Late", "late@example.com").statusCode());
        assertEquals(20, TestApi.countIds(api.send("GET", "/workshops/" + ws + "/registrations").body()));
    }

    @Test
    void rushNeverOverfills() throws Exception {
        String ws = acceptedWorkshop();
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            sent.add(api.sendAsync("POST", "/workshops/" + ws + "/registrations",
                    "{\"name\":\"R" + i + "\",\"email\":\"rush" + i + "@example.com\"}"));
        }
        int created = 0;
        for (CompletableFuture<HttpResponse<String>> f : sent) {
            HttpResponse<String> res = f.join();
            assertTrue(res.statusCode() == 201 || res.statusCode() == 409, res.statusCode() + " " + res.body());
            if (res.statusCode() == 201) {
                created++;
            }
        }
        assertEquals(20, created);
        assertEquals(20, TestApi.countIds(api.send("GET", "/workshops/" + ws + "/registrations").body()));
    }

    @Test
    void rushWithTheSameEmailRegistersOnce() throws Exception {
        String ws = acceptedWorkshop();
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String email = i % 2 == 0 ? "same@example.com" : "SAME@example.com";
            sent.add(api.sendAsync("POST", "/workshops/" + ws + "/registrations",
                    "{\"name\":\"S\",\"email\":\"" + email + "\"}"));
        }
        int created = 0;
        for (CompletableFuture<HttpResponse<String>> f : sent) {
            int code = f.join().statusCode();
            assertTrue(code == 201 || code == 409, String.valueOf(code));
            created += code == 201 ? 1 : 0;
        }
        assertEquals(1, created);
    }

    // ------------------------------------------------------------ helpers

    private String acceptedWorkshop() throws Exception {
        String ws = workshop(api.newSpeaker());
        rateTwice(ws);
        HttpResponse<String> res = decide(ws, "ACCEPTED");
        assertEquals(200, res.statusCode(), res.body());
        return ws;
    }

    private String workshop(String speaker) throws Exception {
        HttpResponse<String> res = api.send("POST", "/talks",
                "{\"title\":\"Hands on\",\"format\":\"WORKSHOP\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}");
        assertEquals(201, res.statusCode(), res.body());
        return TestApi.firstId(res.body());
    }

    private void rateTwice(String talk) throws Exception {
        assertEquals(200, api.send("PUT", "/talks/" + talk + "/ratings/1", "{\"score\":4}").statusCode());
        assertEquals(200, api.send("PUT", "/talks/" + talk + "/ratings/2", "{\"score\":5}").statusCode());
    }

    private HttpResponse<String> decide(String talk, String status) throws Exception {
        return api.send("PUT", "/talks/" + talk + "/status", "{\"status\":\"" + status + "\"}");
    }

    private HttpResponse<String> register(String ws, String name, String email) throws Exception {
        return api.send("POST", "/workshops/" + ws + "/registrations",
                "{\"name\":\"" + name + "\",\"email\":\"" + email + "\"}");
    }
}
