package no.scienta.cfp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;

/**
 * In-memory stand-ins for the DAOs, for unit tests of the services without a database. Each fake overrides
 * every public method of its DAO, so the JdbcTemplate (null) is never touched. They model the database rules the
 * service relies on: unique email in any case, the 3-talk limit, one rating per reviewer, co-speakers in order.
 */
final class FakeDaos {

    final FakeSpeakerDao speakers = new FakeSpeakerDao();
    final FakeTalkDao talks = new FakeTalkDao();
    final FakeRatingDao ratings = new FakeRatingDao();
    final FakeCoSpeakerDao coSpeakers = new FakeCoSpeakerDao();

    SpeakerService speakerService() {
        return new SpeakerService(speakers, new NotificationService());
    }

    TalkService talkService() {
        return new TalkService(speakers, talks, ratings, coSpeakers, speakerService(), new NotificationService());
    }

    RatingService ratingService() {
        return new RatingService(talks, ratings);
    }

    CoSpeakerService coSpeakerService() {
        return new CoSpeakerService(coSpeakers, speakerService(), talkService());
    }

    StatisticsService statisticsService() {
        return new StatisticsService(speakers, talkService());
    }

    static class FakeSpeakerDao extends SpeakerDao {

        private final Map<Long, Speaker> rows = new LinkedHashMap<>();

        FakeSpeakerDao() {
            super(null);
        }

        @Override
        public Speaker findById(long id) {
            return rows.get(id);
        }

        @Override
        public boolean exists(long id) {
            return rows.containsKey(id);
        }

        @Override
        public boolean emailTaken(String email) {
            return emailTakenInStore(email);
        }

        /** Like the unique index on lower(email). */
        @Override
        public long insert(String name, String email, String bio) {
            if (emailTakenInStore(email)) {
                throw new DuplicateKeyException("ux_speaker_email");
            }
            long id = rows.size() + 1;
            rows.put(id, new Speaker(id, name, email, bio));
            return id;
        }

        @Override
        public int update(long id, String name, String email, String bio) {
            if (!rows.containsKey(id)) {
                return 0;
            }
            rows.put(id, new Speaker(id, name, email, bio));
            return 1;
        }

        @Override
        public Map<Long, String> names(Collection<Long> ids) {
            Map<Long, String> names = new LinkedHashMap<>();
            for (Long id : ids) {
                Speaker row = rows.get(id);
                if (row != null) {
                    names.put(id, row.name());
                }
            }
            return names;
        }

        @Override
        public int count() {
            return rows.size();
        }

        @Override
        public List<Speaker> search(String name) {
            return rows.values().stream()
                    .filter(r -> r.name().toLowerCase().contains(name.toLowerCase()))
                    .collect(Collectors.toList());
        }

        /** Separate from {@link #emailTaken}, so a test can make the check miss a concurrent registration. */
        private boolean emailTakenInStore(String email) {
            return rows.values().stream().anyMatch(r -> r.email().equalsIgnoreCase(email));
        }
    }

    static class FakeTalkDao extends TalkDao {

        private final Map<Long, Talk> rows = new LinkedHashMap<>();

        FakeTalkDao() {
            super(null);
        }

        @Override
        public Optional<Talk> findById(long id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<Talk> findBySpeaker(long speakerId) {
            return rows.values().stream().filter(t -> t.mainSpeakerId() == speakerId).collect(Collectors.toList());
        }

        @Override
        public List<Talk> findAll() {
            return new ArrayList<>(rows.values());
        }

        /** No locking in memory: the tests are single-threaded. */
        @Override
        public Optional<Talk> lock(long id) {
            return findById(id);
        }

        @Override
        public void updateStatus(long id, TalkStatus status) {
            Talk t = rows.get(id);
            rows.put(id, new Talk(t.id(), t.title(), t.format(), t.level(), t.mainSpeakerId(), t.durationMinutes(),
                    status, t.submittedAt()));
        }

        @Override
        public boolean exists(long id) {
            return rows.containsKey(id);
        }

        @Override
        public Optional<Long> insertIfBelow(int max, Submission s, Instant submittedAt) {
            if (findBySpeaker(s.speakerId()).size() >= max) {
                return Optional.empty();
            }
            long id = rows.size() + 1;
            rows.put(id, new Talk(id, s.title(), s.format().name(), s.level().name(), s.speakerId(),
                    s.format().durationMinutes, new TalkStatus.Submitted(), submittedAt));
            return Optional.of(id);
        }
    }

    static class FakeRatingDao extends RatingDao {

        /** talkId -> reviewerId -> score; a reviewer's new rating replaces the old one. */
        private final Map<Long, Map<Long, Integer>> scores = new LinkedHashMap<>();

        FakeRatingDao() {
            super(null);
        }

        @Override
        public void save(long talkId, long reviewerId, Score score, String ratedAt) {
            scores.computeIfAbsent(talkId, k -> new LinkedHashMap<>()).put(reviewerId, score.value());
        }

        @Override
        public Map<Long, RatingSummary> summaries(Collection<Long> talkIds) {
            Map<Long, RatingSummary> stats = new LinkedHashMap<>();
            for (Long talkId : talkIds) {
                Map<Long, Integer> forTalk = scores.get(talkId);
                if (forTalk != null && !forTalk.isEmpty()) {
                    stats.put(talkId, new RatingSummary(forTalk.size(), forTalk.values().stream().mapToLong(Integer::longValue).sum()));
                }
            }
            return stats;
        }
    }

    static class FakeCoSpeakerDao extends CoSpeakerDao {

        private final Map<Long, List<Long>> rows = new LinkedHashMap<>();

        FakeCoSpeakerDao() {
            super(null);
        }

        @Override
        public List<Long> findByTalk(long talkId) {
            return new ArrayList<>(rows.getOrDefault(talkId, new ArrayList<>()));
        }

        @Override
        public Map<Long, List<Long>> findByTalks(Collection<Long> talkIds) {
            Map<Long, List<Long>> result = new LinkedHashMap<>();
            for (Long talkId : talkIds) {
                List<Long> list = rows.get(talkId);
                if (list != null && !list.isEmpty()) {
                    result.put(talkId, new ArrayList<>(list));
                }
            }
            return result;
        }

        @Override
        public void add(long talkId, long speakerId) {
            List<Long> list = rows.computeIfAbsent(talkId, k -> new ArrayList<>());
            if (!list.contains(speakerId)) {
                list.add(speakerId);
            }
        }

        @Override
        public void remove(long talkId, long speakerId) {
            rows.getOrDefault(talkId, new ArrayList<>()).remove(Long.valueOf(speakerId));
        }
    }
}
