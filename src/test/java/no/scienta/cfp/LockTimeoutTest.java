package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A request waiting for a row lock gives up after {@code cfp.lock-timeout} with a 503 and {@code Retry-After}, instead
 * of holding one of the five pooled connections for as long as the lock holder likes (docs/adr/0001-talk-row-lock.md).
 * Real app, throwaway database; a second connection plays the stuck lock holder.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "cfp.lock-timeout=500ms")
class LockTimeoutTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    DataSource dataSource;

    private TestApi api;

    @BeforeEach
    void setUp() {
        api = new TestApi(port);
    }

    @Test
    void decisionOnALockedTalkGivesUpWith503() throws Exception {
        String talk = api.newTalk(api.newSpeaker());
        HttpResponse<String> res;
        long started;
        try (Connection holder = holdLock("SELECT id FROM talk WHERE id = ? FOR UPDATE", Long.parseLong(talk))) {
            started = System.nanoTime();
            res = within(api.sendAsync("PUT", "/talks/" + talk + "/status", "{\"status\":\"REJECTED\"}"));
            holder.rollback();
        }
        assertBusy(res, started);
        assertEquals(200, api.send("PUT", "/talks/" + talk + "/status", "{\"status\":\"REJECTED\"}").statusCode(),
                "once the lock is released the same request goes through");
    }

    @Test
    void submissionForALockedSpeakerGivesUpWith503() throws Exception {
        String speaker = api.newSpeaker();
        HttpResponse<String> res;
        long started;
        try (Connection holder = holdLock("SELECT id FROM speaker WHERE id = ? FOR UPDATE", Long.parseLong(speaker))) {
            started = System.nanoTime();
            res = within(api.sendAsync("POST", "/talks", TestApi.talk("Blocked", speaker)));
            holder.rollback();
        }
        assertBusy(res, started);
        assertEquals(201, api.send("POST", "/talks", TestApi.talk("Blocked", speaker)).statusCode());
    }

    @Test
    void placementInALockedSlotGivesUpWith503() throws Exception {
        String talk = api.newTalk(api.newSpeaker());
        api.send("PUT", "/talks/" + talk + "/ratings/1", "{\"score\":4}");
        api.send("PUT", "/talks/" + talk + "/ratings/2", "{\"score\":4}");
        assertEquals(200, api.send("PUT", "/talks/" + talk + "/status", "{\"status\":\"ACCEPTED\"}").statusCode());
        HttpResponse<String> res;
        long started;
        try (Connection holder = holdLock("SELECT slot FROM programme_slot WHERE slot = ? FOR UPDATE", "S1")) {
            started = System.nanoTime();
            res = within(api.sendAsync("PUT", "/programme/R1/S1", "{\"talkId\":" + talk + "}"));
            holder.rollback();
        }
        assertBusy(res, started);
    }

    /** A connection outside the app's transactions that holds a row lock until it rolls back. */
    private Connection holdLock(String sql, Object id) throws Exception {
        Connection c = dataSource.getConnection();
        c.setAutoCommit(false);
        try (PreparedStatement s = c.prepareStatement(sql)) {
            s.setObject(1, id);
            s.executeQuery().close();
        }
        return c;
    }

    /** The response, or a failure if the app is still waiting for the lock after 4 s (it would wait forever). */
    private static HttpResponse<String> within(CompletableFuture<HttpResponse<String>> pending) throws Exception {
        try {
            return pending.get(4, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return fail("still waiting for the row lock after 4 s");
        }
    }

    private static void assertBusy(HttpResponse<String> res, long started) {
        Duration waited = Duration.ofNanos(System.nanoTime() - started);
        assertEquals(503, res.statusCode(), res.body());
        assertEquals("1", res.headers().firstValue("Retry-After").orElse(null));
        assertTrue(res.body().matches("\\{\"timestamp\":\"[^\"]+\",\"status\":503,\"error\":\"Service Unavailable\",\"path\":\"[^\"]+\"}"),
                "the same error body as every other error: " + res.body());
        assertTrue(waited.compareTo(Duration.ofMillis(400)) >= 0, "waited for the lock first: " + waited);
        assertTrue(waited.compareTo(Duration.ofSeconds(4)) < 0, "gave up after the lock timeout: " + waited);
    }
}
