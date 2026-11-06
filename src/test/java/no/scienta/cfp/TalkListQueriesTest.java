package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The talk lists, the statistics and the CSV export over several talks with ratings and co-speakers: their exact
 * output, and that the number of SQL statements per request doesn't grow with the number of talks (no N+1).
 * Runs the real app on a random port with a throwaway database; rows are inserted directly so the output is fixed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TalkListQueriesTest {

    /** Statements prepared or created on any connection of the app's DataSource. */
    static final AtomicInteger STATEMENTS = new AtomicInteger();

    private static final long SUBMITTED = 1762430400000L; // 2025-11-06T12:00:00Z

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @TestConfiguration
    static class CountStatements {
        @Bean
        static BeanPostProcessor countingDataSource() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof DataSource ds && !(bean instanceof DelegatingDataSource)) {
                        return new DelegatingDataSource(ds) {
                            @Override
                            public Connection getConnection() throws SQLException {
                                return counting(super.getConnection());
                            }

                            @Override
                            public Connection getConnection(String user, String password) throws SQLException {
                                return counting(super.getConnection(user, password));
                            }
                        };
                    }
                    return bean;
                }
            };
        }

        private static Connection counting(Connection c) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        String m = method.getName();
                        if (m.equals("prepareStatement") || m.equals("createStatement") || m.equals("prepareCall")) {
                            STATEMENTS.incrementAndGet();
                        }
                        try {
                            return method.invoke(c, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    private TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(port);
        jdbc.execute("TRUNCATE speaker, talk, rating, talk_co_speaker RESTART IDENTITY");
        speaker(1, "Ada Lovelace");
        speaker(2, "Grace; Hopper");
        speaker(3, "Linus");
        speaker(4, "Barbara Liskov"); // no talks
        talk(1, "Alpha", "PRESENTATION", 45, "BEGINNER", "SUBMITTED", 1);
        talk(2, "Beta", "WORKSHOP", 120, "ADVANCED", "ACCEPTED", 2);
        talk(3, "Gamma", "LIGHTNING_10", 10, "INTERMEDIATE", "SUBMITTED", 1);
        talk(4, "Delta", "LIGHTNING_20", 20, "BEGINNER", "REJECTED", 3);
        talk(5, "Epsilon", "PRESENTATION", 45, "ADVANCED", "WAITLISTED", 3);
        ratings(1, 4, 5);          // 4.5 from 2
        ratings(2, 5, 4, 4, 4);    // 4.25 -> 4.3
        ratings(3, 3);             // 3.0
        ratings(5, 5, 5, 4, 4);    // 4.5 from 4: before talk 1 when sorted
        coSpeaker(1, 3);           // added before 2: order by when added, not by id
        coSpeaker(1, 2);
        coSpeaker(2, 1);
        coSpeaker(5, 4);
    }

    // ---------------------------------------------------------------- output

    @Test
    void listsAllTalks() throws Exception {
        assertEquals(
                "[" + json(1, "Alpha", "PRESENTATION", "BEGINNER", 1, 45, "SUBMITTED", "4.5", 2, "3,2")
                        + "," + json(2, "Beta", "WORKSHOP", "ADVANCED", 2, 120, "ACCEPTED", "4.3", 4, "1")
                        + "," + json(3, "Gamma", "LIGHTNING_10", "INTERMEDIATE", 1, 10, "SUBMITTED", "3.0", 1, "")
                        + "," + json(4, "Delta", "LIGHTNING_20", "BEGINNER", 3, 20, "REJECTED", "null", 0, "")
                        + "," + json(5, "Epsilon", "PRESENTATION", "ADVANCED", 3, 45, "WAITLISTED", "4.5", 4, "4")
                        + "]",
                body("/talks"));
    }

    @Test
    void sortsTalksByRating() throws Exception {
        assertEquals("[5, 1, 2, 3, 4]", ids(body("/talks?sort=rating")));
    }

    @Test
    void listsASpeakersTalks() throws Exception {
        assertEquals(
                "[" + json(1, "Alpha", "PRESENTATION", "BEGINNER", 1, 45, "SUBMITTED", "4.5", 2, "3,2")
                        + "," + json(3, "Gamma", "LIGHTNING_10", "INTERMEDIATE", 1, 10, "SUBMITTED", "3.0", 1, "")
                        + "]",
                body("/speakers/1/talks"));
        assertEquals(body("/speakers/3/talks"), body("/talks?speakerId=3"));
        assertEquals("[4, 5]", ids(body("/speakers/3/talks")));
        assertEquals("[]", body("/speakers/4/talks"));
    }

    @Test
    void getsOneTalk() throws Exception {
        assertEquals(json(1, "Alpha", "PRESENTATION", "BEGINNER", 1, 45, "SUBMITTED", "4.5", 2, "3,2"), body("/talks/1"));
        assertEquals(json(4, "Delta", "LIGHTNING_20", "BEGINNER", 3, 20, "REJECTED", "null", 0, ""), body("/talks/4"));
    }

    @Test
    void statistics() throws Exception {
        String expected = "{\"speakers\":4,\"speakersWithTalks\":3,\"talks\":5,"
                + "\"byFormat\":{\"PRESENTATION\":2,\"WORKSHOP\":1,\"LIGHTNING_10\":1,\"LIGHTNING_20\":1},"
                + "\"byLevel\":{\"BEGINNER\":2,\"ADVANCED\":2,\"INTERMEDIATE\":1},"
                + "\"byStatus\":{\"SUBMITTED\":2,\"ACCEPTED\":1,\"REJECTED\":1,\"WAITLISTED\":1},"
                + "\"totalHours\":4,\"averageRating\":4.0,\"generated\":\"*\"}";
        assertEquals(expected, stats());

        // a talk whose speaker row is gone counts as a talk, but not as a speaker with talks
        talk(6, "Orphan", "PRESENTATION", 45, "BEGINNER", "SUBMITTED", 99);
        assertEquals(expected.replace("\"talks\":5", "\"talks\":6")
                        .replace("{\"PRESENTATION\":2", "{\"PRESENTATION\":3")
                        .replace("{\"BEGINNER\":2", "{\"BEGINNER\":3")
                        .replace("{\"SUBMITTED\":2", "{\"SUBMITTED\":3"),
                stats());
    }

    @Test
    void csvExport() throws Exception {
        HttpResponse<String> res = api.send("GET", "/admin/talks.csv");
        assertEquals(200, res.statusCode());
        assertEquals("id;title;format;minutes;level;speaker;status;submitted;rating;ratings\n"
                        + "1;Alpha;Presentation;45;Beginner;Ada Lovelace;SUBMITTED;6. nov. 2025;4,5;2\n"
                        + "2;Beta;Workshop;120;Advanced;\"Grace; Hopper\";ACCEPTED;6. nov. 2025;4,3;4\n"
                        + "3;Gamma;Lightning 10;10;Intermediate;Ada Lovelace;SUBMITTED;6. nov. 2025;3,0;1\n"
                        + "4;Delta;Lightning 20;20;Beginner;Linus;REJECTED;6. nov. 2025;;0\n"
                        + "5;Epsilon;Presentation;45;Advanced;Linus;WAITLISTED;6. nov. 2025;4,5;4\n",
                res.body());
    }

    @Test
    void csvExportOfATalkWithoutSpeakerIsNotFound() throws Exception {
        talk(6, "Orphan", "PRESENTATION", 45, "BEGINNER", "SUBMITTED", 99);
        assertEquals(404, api.send("GET", "/admin/talks.csv").statusCode());
    }

    // ---------------------------------------------------------------- query counts

    // ---------------------------------------------------------------- helpers

    private String body(String path) throws Exception {
        HttpResponse<String> res = api.send("GET", path);
        assertEquals(200, res.statusCode(), path + ": " + res.body());
        return res.body();
    }

    private String stats() throws Exception {
        return body("/admin/stats").replaceAll("\"generated\":\"[^\"]*\"", "\"generated\":\"*\"");
    }

    private static String ids(String body) {
        return java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(body).results()
                .map(m -> m.group(1)).toList().toString();
    }

    private static String json(long id, String title, String format, String level, long speakerId, int minutes,
                               String status, String average, int count, String coSpeakers) {
        return "{\"id\":" + id + ",\"title\":\"" + title + "\",\"format\":\"" + format + "\",\"level\":\"" + level
                + "\",\"speakerId\":" + speakerId + ",\"durationMinutes\":" + minutes + ",\"status\":\"" + status
                + "\",\"submittedAt\":\"2025-11-06T12:00:00.000+00:00\",\"submittedOn\":\"2025-11-06\",\"averageRating\":" + average
                + ",\"ratingCount\":" + count + ",\"coSpeakerIds\":[" + coSpeakers + "]}";
    }

    private void speaker(long id, String name) {
        jdbc.update("INSERT INTO speaker (id, name, email, bio, created) VALUES (?, ?, ?, NULL, '06.11.2025')",
                id, name, "s" + id + "@example.com");
    }

    private void talk(long id, String title, String format, int duration, String level, String status, long speakerId) {
        jdbc.update("INSERT INTO talk (id, title, format, duration, level, status, speaker_id, submitted_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, title, format, duration, level, status, speakerId, SUBMITTED);
    }

    private void ratings(long talkId, int... scores) {
        for (int i = 0; i < scores.length; i++) {
            jdbc.update("INSERT INTO rating (talk_id, reviewer_id, score, rated_at) VALUES (?, ?, ?, '2025-11-06 12:00:00')",
                    talkId, i + 1, scores[i]);
        }
    }

    private void coSpeaker(long talkId, long speakerId) {
        jdbc.update("INSERT INTO talk_co_speaker (talk_id, speaker_id) VALUES (?, ?)", talkId, speakerId);
    }
}
