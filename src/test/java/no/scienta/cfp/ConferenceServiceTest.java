package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The service's rules, on in-memory fakes of the DAOs (no database, no HTTP). */
class ConferenceServiceTest {

    private ConferenceService service;

    @BeforeEach
    void setUp() {
        service = new FakeDaos().service();
    }

    // ---------------------------------------------------------------- speakers

    @Test
    void duplicateEmailIsConflictInAnyCase() {
        register("ada@example.com");
        assertStatus(HttpStatus.CONFLICT, () -> service.registerSpeaker(speaker("ADA@example.com")));
    }

    @Test
    void emailRegisteredConcurrentlyIsConflict() {
        // the pre-check misses a registration that lands just after it; the unique index catches it
        FakeDaos racy = new FakeDaos();
        ConferenceService racyService = new ConferenceService(new FakeDaos.FakeSpeakerDao() {
            @Override
            public boolean emailTaken(String email) {
                return false;
            }
        }, racy.talks, racy.ratings, racy.coSpeakers, new NotificationService());
        racyService.registerSpeaker(speaker("ada@example.com"));
        assertStatus(HttpStatus.CONFLICT, () -> racyService.registerSpeaker(speaker("Ada@Example.com")));
    }

    // ---------------------------------------------------------------- submitTalk

    @Test
    void talkWithoutTitleIsRejected() {
        TalkForm form = form(register("ada@example.com"));
        form.title = " ";
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.submitTalk(form));
    }

    @Test
    void unknownFormatIsRejected() {
        TalkForm form = form(register("ada@example.com"));
        form.format = "KEYNOTE";
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.submitTalk(form));
    }

    @Test
    void unknownLevelIsRejected() {
        TalkForm form = form(register("ada@example.com"));
        form.level = "EXPERT";
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.submitTalk(form));
    }

    @Test
    void missingSpeakerIdIsRejected() {
        TalkForm form = form(1);
        form.speakerId = null;
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.submitTalk(form));
    }

    @Test
    void unknownSpeakerIsRejected() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.submitTalk(form(7)));
    }

    @ParameterizedTest
    @CsvSource({"LIGHTNING_10, 10", "LIGHTNING_20, 20", "PRESENTATION, 45", "WORKSHOP, 120"})
    void durationFollowsFormat(String format, int minutes) {
        TalkForm form = form(register("ada@example.com"));
        form.format = format;
        TalkDto talk = service.submitTalk(form);
        assertEquals(minutes, talk.durationMinutes);
        assertEquals(minutes, service.getTalk(talk.id).durationMinutes);
    }

    @Test
    void atMostThreeTalksPerSpeaker() {
        long ada = register("ada@example.com");
        for (int i = 0; i < 3; i++) {
            service.submitTalk(form(ada));
        }
        assertStatus(HttpStatus.CONFLICT, () -> service.submitTalk(form(ada)));
    }

    // ---------------------------------------------------------------- average rating

    @ParameterizedTest
    @CsvSource({"'5,4,4,4', 4.3", "'5,4', 4.5", "'5,4,4', 4.3", "'4,4,3', 3.7", "'5', 5.0"})
    void averageHasOneDecimalRoundedHalfUp(String scores, double expected) {
        long talk = newTalk();
        String[] s = scores.split(",");
        for (int reviewer = 0; reviewer < s.length; reviewer++) {
            service.rate(talk, reviewer + 1, score(Integer.parseInt(s[reviewer])));
        }
        TalkDto t = service.getTalk(talk);
        assertEquals(expected, t.averageRating);
        assertEquals(s.length, t.ratingCount);
    }

    @Test
    void unratedTalkHasNoAverage() {
        TalkDto t = service.getTalk(newTalk());
        assertNull(t.averageRating);
        assertEquals(0, t.ratingCount);
    }

    @Test
    void unknownTalkIsNotFound() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.getTalk(99));
    }

    // ---------------------------------------------------------------- sort=rating

    @Test
    void sortByRatingThenCountThenIdWithUnratedLast() {
        long t1 = newTalk();            // unrated
        long t2 = newTalk(4);           // 4.0, 1 rating
        long t3 = newTalk(4, 4);        // 4.0, 2 ratings
        long t4 = newTalk(5);           // 5.0
        long t5 = newTalk(4);           // 4.0, 1 rating, higher id than t2
        long t6 = newTalk();            // unrated
        List<Long> ids = service.listTalks("rating", null).stream().map(t -> t.id).collect(Collectors.toList());
        assertEquals(Arrays.asList(t4, t3, t2, t5, t1, t6), ids);
    }

    @Test
    void unknownSortIsRejected() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.listTalks("title", null));
    }

    // ---------------------------------------------------------------- rate

    @Test
    void ratingIsEchoedAndCounted() {
        long talk = newTalk();
        Map<String, Object> result = service.rate(talk, 2, score(4));
        assertEquals(talk, result.get("talkId"));
        assertEquals(2L, result.get("reviewerId"));
        assertEquals(4, result.get("score"));
        assertEquals(1, service.getTalk(talk).ratingCount);
    }

    @Test
    void reviewerRatingAgainReplacesTheirRating() {
        long talk = newTalk();
        service.rate(talk, 2, score(1));
        service.rate(talk, 2, score(5));
        TalkDto t = service.getTalk(talk);
        assertEquals(1, t.ratingCount);
        assertEquals(5.0, t.averageRating);
    }

    @Test
    void ratedAtDisplayIsUsShortFormat() {
        String display = (String) service.rate(newTalk(), 2, score(4)).get("ratedAtDisplay");
        // a plain space before AM/PM: JDK 20+ would put U+202F there
        assertTrue(display.matches("\\d{1,2}/\\d{1,2}/\\d{2}, \\d{1,2}:\\d{2} [AP]M"), display);
    }

    @ParameterizedTest
    @CsvSource({"0", "6", "-1"})
    void scoreOutsideOneToFiveIsRejected(int score) {
        long talk = newTalk();
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.rate(talk, 2, score(score)));
    }

    @Test
    void scoreThatIsNotAWholeNumberIsRejected() {
        long talk = newTalk();
        for (Object score : Arrays.asList(4.0, 4.5, "4", null, true, 3_000_000_000L)) {
            assertStatus(HttpStatus.BAD_REQUEST, () -> service.rate(talk, 2, score(score)));
        }
        assertEquals(0, service.getTalk(talk).ratingCount);
    }

    @Test
    void reviewerIdMustBePositive() {
        long talk = newTalk();
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.rate(talk, 0, score(4)));
    }

    @Test
    void ratingUnknownTalkIsNotFound() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.rate(99, 2, score(4)));
    }

    // ---------------------------------------------------------------- helpers

    private static void assertStatus(HttpStatus status, Executable call) {
        assertEquals(status, assertThrows(ResponseStatusException.class, call).getStatusCode());
    }

    private long register(String email) {
        return ((Number) service.registerSpeaker(speaker(email)).get("id")).longValue();
    }

    private int speakers;

    /** A talk by a fresh speaker, rated with the given scores by reviewers 1, 2, ... */
    private long newTalk(int... scores) {
        long speaker = register("speaker-" + (++speakers) + "@example.com");
        long talk = service.submitTalk(form(speaker)).id;
        for (int i = 0; i < scores.length; i++) {
            service.rate(talk, i + 1, score(scores[i]));
        }
        return talk;
    }

    private static SpeakerForm speaker(String email) {
        SpeakerForm form = new SpeakerForm();
        form.setName("Ada");
        form.setEmail(email);
        return form;
    }

    private static TalkForm form(long speakerId) {
        TalkForm form = new TalkForm();
        form.title = "Legacy is a feeling";
        form.format = "PRESENTATION";
        form.level = "INTERMEDIATE";
        form.speakerId = speakerId;
        return form;
    }

    private static Map<String, Object> score(Object score) {
        Map<String, Object> body = new HashMap<>();
        body.put("score", score);
        return body;
    }
}
