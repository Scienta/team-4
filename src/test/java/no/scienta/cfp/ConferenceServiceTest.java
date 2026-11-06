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

/** The services' rules (and through them the domain types'), on in-memory fakes of the DAOs (no database, no HTTP). */
class ConferenceServiceTest {

    private SpeakerService speakerService;
    private TalkService talkService;
    private RatingService ratingService;
    private CoSpeakerService coSpeakerService;
    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        FakeDaos daos = new FakeDaos();
        speakerService = daos.speakerService();
        talkService = daos.talkService();
        ratingService = daos.ratingService();
        coSpeakerService = daos.coSpeakerService();
        statisticsService = daos.statisticsService();
    }

    // ---------------------------------------------------------------- speakers

    @Test
    void duplicateEmailIsConflictInAnyCase() {
        register("ada@example.com");
        assertStatus(HttpStatus.CONFLICT, () -> speakerService.registerSpeaker(speaker("ADA@example.com")));
    }

    @Test
    void emailRegisteredConcurrentlyIsConflict() {
        // the pre-check misses a registration that lands just after it; the unique index catches it
        SpeakerService racyService = new SpeakerService(new FakeDaos.FakeSpeakerDao() {
            @Override
            public boolean emailTaken(String email) {
                return false;
            }
        }, new NotificationService());
        racyService.registerSpeaker(speaker("ada@example.com"));
        assertStatus(HttpStatus.CONFLICT, () -> racyService.registerSpeaker(speaker("Ada@Example.com")));
    }

    // ---------------------------------------------------------------- submitTalk

    @Test
    void talkWithoutTitleIsRejected() {
        TalkForm form = form(register("ada@example.com"));
        form.title = " ";
        assertStatus(HttpStatus.BAD_REQUEST, () -> talkService.submitTalk(form));
    }

    @Test
    void unknownFormatIsRejected() {
        TalkForm form = form(register("ada@example.com"));
        form.format = "KEYNOTE";
        assertError(HttpStatus.BAD_REQUEST, "format must be one of [WORKSHOP, PRESENTATION, LIGHTNING_20, LIGHTNING_10]",
                () -> talkService.submitTalk(form));
    }

    @Test
    void formatIsCaseSensitive() {
        TalkForm form = form(register("ada@example.com"));
        form.format = "presentation";
        assertStatus(HttpStatus.BAD_REQUEST, () -> talkService.submitTalk(form));
    }

    @Test
    void unknownLevelIsRejected() {
        TalkForm form = form(register("ada@example.com"));
        form.level = "EXPERT";
        assertError(HttpStatus.BAD_REQUEST, "unknown level", () -> talkService.submitTalk(form));
    }

    @Test
    void levelIsCaseSensitive() {
        TalkForm form = form(register("ada@example.com"));
        form.level = "beginner";
        assertStatus(HttpStatus.BAD_REQUEST, () -> talkService.submitTalk(form));
    }

    @Test
    void missingFormatAndLevelAreRejected() {
        TalkForm noFormat = form(register("ada@example.com"));
        noFormat.format = null;
        assertStatus(HttpStatus.BAD_REQUEST, () -> talkService.submitTalk(noFormat));
        TalkForm noLevel = form(register("bob@example.com"));
        noLevel.level = null;
        assertStatus(HttpStatus.BAD_REQUEST, () -> talkService.submitTalk(noLevel));
    }

    @Test
    void missingSpeakerIdIsRejected() {
        TalkForm form = form(1);
        form.speakerId = null;
        assertStatus(HttpStatus.BAD_REQUEST, () -> talkService.submitTalk(form));
    }

    @Test
    void unknownSpeakerIsRejected() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> talkService.submitTalk(form(7)));
    }

    @ParameterizedTest
    @CsvSource({"LIGHTNING_10, 10", "LIGHTNING_20, 20", "PRESENTATION, 45", "WORKSHOP, 120"})
    void durationFollowsFormat(String format, int minutes) {
        TalkForm form = form(register("ada@example.com"));
        form.format = format;
        TalkView talk = talkService.submitTalk(form);
        assertEquals(minutes, talk.durationMinutes());
        assertEquals(minutes, talkService.getTalk(talk.id()).durationMinutes());
    }

    @Test
    void atMostThreeTalksPerSpeaker() {
        long ada = register("ada@example.com");
        for (int i = 0; i < 3; i++) {
            talkService.submitTalk(form(ada));
        }
        assertError(HttpStatus.CONFLICT, "a speaker can submit at most 3 talks", () -> talkService.submitTalk(form(ada)));
    }

    // ---------------------------------------------------------------- average rating

    @ParameterizedTest
    // Ratings change request: 2 decimals (was 1, halves up), trimmed at 5+ ratings (RatingSummaryTest).
    @CsvSource({"'5,4,4,4', 4.25", "'5,4', 4.5", "'5,4,4', 4.33", "'4,4,3', 3.67", "'5', 5.0", "'5,5,5,1,1', 3.67"})
    void averageHasTwoDecimalsAndIsTrimmedFromFiveRatings(String scores, double expected) {
        long talk = newTalk();
        String[] s = scores.split(",");
        for (int reviewer = 0; reviewer < s.length; reviewer++) {
            ratingService.rate(talk, reviewer + 1, score(Integer.parseInt(s[reviewer])));
        }
        TalkView t = talkService.getTalk(talk);
        assertEquals(expected, t.averageRating());
        assertEquals(s.length, t.ratingCount());
    }

    @Test
    void unratedTalkHasNoAverage() {
        TalkView t = talkService.getTalk(newTalk());
        assertNull(t.averageRating());
        assertEquals(0, t.ratingCount());
    }

    @Test
    void unknownTalkIsNotFound() {
        assertStatus(HttpStatus.NOT_FOUND, () -> talkService.getTalk(99));
    }

    // ---------------------------------------------------------------- statistics

    @Test
    void statsAverageIsCutOffUnlikeTheTalkAverage() {
        newTalk(4);          // 4.0
        newTalk(5, 4);       // 4.5
        newTalk();           // unrated, not counted
        // (4.0 + 4.5) / 2 = 4.25, cut off to 4.2 (CFP-61); a talk's own 4.25 would show as 4.3
        assertEquals(4.2, statisticsService.statistics().get("averageRating"));
    }

    @Test
    void statsAverageIsNullWithoutRatings() {
        newTalk();
        assertNull(statisticsService.statistics().get("averageRating"));
    }

    // ---------------------------------------------------------------- sort=rating

    /** Ratings change request: the ranking uses the trimmed average (1,5,5,5,5 is 5.0, not 4.2, so above 4.5). */
    @Test
    void rankingUsesTheTrimmedAverage() {
        long outlierHit = newTalk();
        long steady = newTalk();
        int reviewer = 1;
        for (int score : new int[] {1, 5, 5, 5, 5}) {
            ratingService.rate(outlierHit, reviewer++, score(score));
        }
        ratingService.rate(steady, reviewer++, score(5));
        ratingService.rate(steady, reviewer, score(4));
        List<TalkView> ranked = talkService.listTalks("rating", null);
        assertEquals(List.of(outlierHit, steady), ranked.stream().map(TalkView::id).toList());
        assertEquals(5.0, ranked.get(0).averageRating());
        assertEquals(5, ranked.get(0).ratingCount());
    }

    @Test
    void sortByRatingThenCountThenIdWithUnratedLast() {
        long t1 = newTalk();            // unrated
        long t2 = newTalk(4);           // 4.0, 1 rating
        long t3 = newTalk(4, 4);        // 4.0, 2 ratings
        long t4 = newTalk(5);           // 5.0
        long t5 = newTalk(4);           // 4.0, 1 rating, higher id than t2
        long t6 = newTalk();            // unrated
        List<Long> ids = talkService.listTalks("rating", null).stream().map(t -> t.id()).collect(Collectors.toList());
        assertEquals(Arrays.asList(t4, t3, t2, t5, t1, t6), ids);
    }

    @Test
    void unknownSortIsRejected() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> talkService.listTalks("title", null));
    }

    // ---------------------------------------------------------------- rate

    @Test
    void ratingIsEchoedAndCounted() {
        long talk = newTalk();
        Rating result = ratingService.rate(talk, 2, score(4));
        assertEquals(talk, result.talkId());
        assertEquals(2L, result.reviewerId());
        assertEquals(4, result.score());
        assertEquals(1, talkService.getTalk(talk).ratingCount());
    }

    @Test
    void reviewerRatingAgainReplacesTheirRating() {
        long talk = newTalk();
        ratingService.rate(talk, 2, score(1));
        ratingService.rate(talk, 2, score(5));
        TalkView t = talkService.getTalk(talk);
        assertEquals(1, t.ratingCount());
        assertEquals(5.0, t.averageRating());
    }

    @Test
    void ratedAtDisplayIsUsShortFormat() {
        String display = ratingService.rate(newTalk(), 2, score(4)).ratedAtDisplay();
        // a plain space before AM/PM: JDK 20+ would put U+202F there
        assertTrue(display.matches("\\d{1,2}/\\d{1,2}/\\d{2}, \\d{1,2}:\\d{2} [AP]M"), display);
    }

    @ParameterizedTest
    @CsvSource({"0", "6", "-1"})
    void scoreOutsideOneToFiveIsRejected(int score) {
        long talk = newTalk();
        assertStatus(HttpStatus.BAD_REQUEST, () -> ratingService.rate(talk, 2, score(score)));
    }

    @Test
    void scoreThatIsNotAWholeNumberIsRejected() {
        long talk = newTalk();
        for (Object score : Arrays.asList(4.0, 4.5, "4", null, true, 3_000_000_000L)) {
            assertStatus(HttpStatus.BAD_REQUEST, () -> ratingService.rate(talk, 2, score(score)));
        }
        assertEquals(0, talkService.getTalk(talk).ratingCount());
    }

    @Test
    void reviewerIdMustBePositive() {
        long talk = newTalk();
        assertStatus(HttpStatus.BAD_REQUEST, () -> ratingService.rate(talk, 0, score(4)));
    }

    @Test
    void ratingUnknownTalkIsNotFound() {
        assertStatus(HttpStatus.NOT_FOUND, () -> ratingService.rate(99, 2, score(4)));
    }

    // ---------------------------------------------------------------- co-speakers

    @Test
    void mainSpeakerCannotBeTheirOwnCoSpeaker() {
        long main = register("main@example.com");
        long talk = talkService.submitTalk(form(main)).id();
        assertStatus(HttpStatus.BAD_REQUEST, () -> coSpeakerService.addCoSpeaker(talk, main));
    }

    @Test
    void removingSomeoneWhoIsNotACoSpeakerIsNotFound() {
        long talk = newTalk();
        long ada = register("ada@example.com");
        assertStatus(HttpStatus.NOT_FOUND, () -> coSpeakerService.removeCoSpeaker(talk, ada));
    }

    @Test
    void removedCoSpeakerCanNotBeRemovedTwice() {
        long talk = newTalk();
        long ada = register("ada@example.com");
        coSpeakerService.addCoSpeaker(talk, ada);
        assertEquals(Arrays.asList(), coSpeakerService.removeCoSpeaker(talk, ada).coSpeakerIds());
        assertStatus(HttpStatus.NOT_FOUND, () -> coSpeakerService.removeCoSpeaker(talk, ada));
    }

    // ---------------------------------------------------------------- helpers

    private static void assertStatus(HttpStatus status, Executable call) {
        assertEquals(status, ApiErrors.status(assertThrows(Refused.class, call).kind));
    }

    private static void assertError(HttpStatus status, String reason, Executable call) {
        Refused e = assertThrows(Refused.class, call);
        assertEquals(status, ApiErrors.status(e.kind));
        assertEquals(reason, e.getMessage());
    }

    private long register(String email) {
        return speakerService.registerSpeaker(speaker(email)).id();
    }

    private int speakers;

    /** A talk by a fresh speaker, rated with the given scores by reviewers 1, 2, ... */
    private long newTalk(int... scores) {
        long speaker = register("speaker-" + (++speakers) + "@example.com");
        long talk = talkService.submitTalk(form(speaker)).id();
        for (int i = 0; i < scores.length; i++) {
            ratingService.rate(talk, i + 1, score(scores[i]));
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
