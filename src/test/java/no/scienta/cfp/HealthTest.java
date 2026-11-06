package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.postgresql.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.zaxxer.hikari.HikariDataSource;

/**
 * The deploy contract's GET /health: 200 with the baked-in version while the database answers, 503 within 5 s
 * when it doesn't. The database here is a container of its own, so the test can pause it without hurting others.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "GIT_SHA=0123abc")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HealthTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        TestDatabase.pointAt(registry, POSTGRES, POSTGRES.getDatabaseName());
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    DataSource dataSource;

    private final HttpClient http = HttpClient.newHttpClient();

    // /health shares the platform's 5-connection pool. With Hikari's default 30 s connection timeout, a burst that
    // holds all 5 connections leaves starved requests (and the probe) waiting 30 s; the pool must give up sooner.
    @Test
    @Order(0)
    void poolGivesUpOnAConnectionWellBeforeHikarisThirtySecondDefault() throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        assertEquals(5, hikari.getMaximumPoolSize());
        assertEquals(10_000, hikari.getConnectionTimeout());
    }

    // Without a socket timeout a read on a half-open connection blocks forever (the query timeout's cancel goes
    // over a new connection, which fails too), so the health probe never finishes and /health stays 503.
    @Test
    @Order(0)
    void theDriverGetsASocketTimeout() throws Exception {
        String url = dataSource.unwrap(HikariDataSource.class).getJdbcUrl();
        Properties parsed = Driver.parseURL(url, null);
        assertEquals("30", parsed.getProperty("socketTimeout"), url);
        assertEquals("disable", parsed.getProperty("sslmode"), url);
    }

    @Test
    @Order(1)
    void healthIsOkWithTheVersionWhileTheDatabaseAnswers() throws Exception {
        HttpResponse<String> res = health();
        assertEquals(200, res.statusCode(), res.body());
        assertEquals("{\"status\":\"ok\",\"version\":\"0123abc\"}", res.body());
    }

    @Test
    @Order(2)
    void healthIsDbUnavailableWithinFiveSecondsWhenTheDatabaseHangs() throws Exception {
        POSTGRES.getDockerClient().pauseContainerCmd(POSTGRES.getContainerId()).exec();
        try {
            long started = System.nanoTime();
            HttpResponse<String> res = health();
            long took = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertEquals(503, res.statusCode(), res.body());
            assertEquals("{\"status\":\"db-unavailable\",\"version\":\"0123abc\"}", res.body());
            assertTrue(took < 6000, "took " + took + " ms");
        } finally {
            unpause();
        }
    }

    @Test
    @Order(3)
    void probesThatTimeOutDoNotPileUpWhileTheDatabaseHangs() throws Exception {
        POSTGRES.getDockerClient().pauseContainerCmd(POSTGRES.getContainerId()).exec();
        try {
            for (int i = 0; i < 3; i++) {
                assertEquals(503, health().statusCode());
            }
            long threads = healthCheckThreads();
            assertTrue(threads <= 1, threads + " health-check threads");
        } finally {
            unpause();
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        int status = health().statusCode();
        while (status != 200 && System.nanoTime() < deadline) {
            Thread.sleep(200);
            status = health().statusCode();
        }
        assertEquals(200, status, "health did not recover once the database came back");
    }

    private static long healthCheckThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.getName().startsWith("health-check"))
                .count();
    }

    // Podman drops the docker client's idle connection while the test waits, so the first try can hit a broken pipe.
    private static void unpause() {
        try {
            POSTGRES.getDockerClient().unpauseContainerCmd(POSTGRES.getContainerId()).exec();
        } catch (RuntimeException e) {
            POSTGRES.getDockerClient().unpauseContainerCmd(POSTGRES.getContainerId()).exec();
        }
    }

    private HttpResponse<String> health() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health"))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
