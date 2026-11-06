package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Programme export as JSON and CSV (workflow/specs/programme-export.md). Real app, throwaway database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProgrammeExportTest {

    private static final String HEADER = "slot,start,end,room,talkId,title,format,speakers\n";

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

    /** The programme is shared by the whole class: every test leaves it empty. */
    @AfterEach
    void clearProgramme() throws Exception {
        for (Room room : Room.values()) {
            for (Slot slot : Slot.values()) {
                api.send("DELETE", "/programme/" + room + "/" + slot);
            }
        }
    }

    @Test
    void emptyProgramme() throws Exception {
        HttpResponse<String> json = api.send("GET", "/programme");
        assertEquals(200, json.statusCode());
        assertEquals("[]", json.body());
        HttpResponse<String> csv = api.send("GET", "/programme.csv");
        assertEquals(200, csv.statusCode());
        assertEquals(HEADER, csv.body());
    }

    @Test
    void wholeProgrammeInDayOrderAsJsonAndCsv() throws Exception {
        String grace = speaker("Grace Hopper");
        String ada = speaker("Ada Lovelace");
        String alan = speaker("Alan Turing");
        String edsger = speaker("Edsger Dijkstra");
        String late = accepted("Late one", "PRESENTATION", grace);
        String workshop = accepted("Kotlin, \"really\" fast", "WORKSHOP", alan);
        String early = accepted("Early bird", "LIGHTNING_10", edsger);
        String paired = accepted("Pairing", "PRESENTATION", ada);
        coSpeaker(paired, alan);
        coSpeaker(paired, grace);

        put("R1", "S5", late);
        put("R3", "S4", workshop);
        put("R2", "S1", early);
        put("R1", "S1", paired);

        HttpResponse<String> json = api.send("GET", "/programme");
        assertEquals(200, json.statusCode(), json.body());
        assertEquals("["
                + "{\"slot\":\"S1\",\"start\":\"09:00\",\"end\":\"10:00\",\"room\":\"R1\",\"talkId\":" + paired
                + ",\"title\":\"Pairing\",\"format\":\"PRESENTATION\",\"speakers\":[\"Ada Lovelace\",\"Alan Turing\",\"Grace Hopper\"]},"
                + "{\"slot\":\"S1\",\"start\":\"09:00\",\"end\":\"10:00\",\"room\":\"R2\",\"talkId\":" + early
                + ",\"title\":\"Early bird\",\"format\":\"LIGHTNING_10\",\"speakers\":[\"Edsger Dijkstra\"]},"
                + "{\"slot\":\"S4\",\"start\":\"13:30\",\"end\":\"15:30\",\"room\":\"R3\",\"talkId\":" + workshop
                + ",\"title\":\"Kotlin, \\\"really\\\" fast\",\"format\":\"WORKSHOP\",\"speakers\":[\"Alan Turing\"]},"
                + "{\"slot\":\"S5\",\"start\":\"15:45\",\"end\":\"16:45\",\"room\":\"R1\",\"talkId\":" + late
                + ",\"title\":\"Late one\",\"format\":\"PRESENTATION\",\"speakers\":[\"Grace Hopper\"]}"
                + "]", json.body());

        HttpResponse<String> csv = api.send("GET", "/programme.csv");
        assertEquals(200, csv.statusCode());
        assertTrue(csv.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"),
                csv.headers().firstValue("Content-Type").toString());
        assertEquals(HEADER
                + "S1,09:00,10:00,R1," + paired + ",Pairing,PRESENTATION,Ada Lovelace; Alan Turing; Grace Hopper\n"
                + "S1,09:00,10:00,R2," + early + ",Early bird,LIGHTNING_10,Edsger Dijkstra\n"
                + "S4,13:30,15:30,R3," + workshop + ",\"Kotlin, \"\"really\"\" fast\",WORKSHOP,Alan Turing\n"
                + "S5,15:45,16:45,R1," + late + ",Late one,PRESENTATION,Grace Hopper\n", csv.body());
    }

    @Test
    void speakersAndStatusAsTheyAreNow() throws Exception {
        String main = speaker("Old Name");
        String talk = accepted("Still here", "PRESENTATION", main);
        put("R2", "S3", talk);

        HttpResponse<String> renamed = api.send("PUT", "/speakers/" + main,
                "{\"name\":\"New Name\",\"email\":\"renamed-" + UUID.randomUUID() + "@example.com\"}");
        assertEquals(200, renamed.statusCode(), renamed.body());
        assertEquals(200, api.send("POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":" + main + "}").statusCode());

        assertTrue(api.send("GET", "/programme").body().contains("\"speakers\":[\"New Name\"]"));
        assertEquals(HEADER + "S3,11:30,12:30,R2," + talk + ",Still here,PRESENTATION,New Name\n",
                api.send("GET", "/programme.csv").body());
    }

    @Test
    void csvEvenWhenTheClientAsksForJson() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/programme.csv"))
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> res = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), res.body());
        assertTrue(res.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"));
        assertEquals(HEADER, res.body());
    }

    @Test
    void csvIsUtf8() throws Exception {
        String talk = accepted("Æ, ø og å", "LIGHTNING_20", speaker("Ådne Øye"));
        put("R3", "S2", talk);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/programme.csv")).GET().build();
        HttpResponse<byte[]> res = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        String body = new String(res.body(), StandardCharsets.UTF_8);
        assertEquals(HEADER + "S2,10:15,11:15,R3," + talk + ",\"Æ, ø og å\",LIGHTNING_20,Ådne Øye\n", body);
    }

    // ------------------------------------------------------------ helpers

    private String speaker(String name) throws Exception {
        HttpResponse<String> res = api.send("POST", "/speakers",
                "{\"name\":\"" + name + "\",\"email\":\"x-" + UUID.randomUUID() + "@example.com\"}");
        assertEquals(201, res.statusCode(), res.body());
        return TestApi.firstId(res.body());
    }

    private String accepted(String title, String format, String speaker) throws Exception {
        String json = "{\"title\":\"" + title.replace("\"", "\\\"") + "\",\"format\":\"" + format
                + "\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}";
        HttpResponse<String> res = api.send("POST", "/talks", json);
        assertEquals(201, res.statusCode(), res.body());
        String talk = TestApi.firstId(res.body());
        assertEquals(200, api.send("PUT", "/talks/" + talk + "/ratings/1", "{\"score\":4}").statusCode());
        assertEquals(200, api.send("PUT", "/talks/" + talk + "/ratings/2", "{\"score\":4}").statusCode());
        assertEquals(200, api.send("PUT", "/talks/" + talk + "/status", "{\"status\":\"ACCEPTED\"}").statusCode());
        return talk;
    }

    private void coSpeaker(String talk, String speaker) throws Exception {
        assertEquals(200, api.send("PUT", "/talks/" + talk + "/co-speakers/" + speaker).statusCode());
    }

    private void put(String room, String slot, String talk) throws Exception {
        HttpResponse<String> res = api.send("PUT", "/programme/" + room + "/" + slot, "{\"talkId\":" + talk + "}");
        assertEquals(200, res.statusCode(), res.body());
    }
}
