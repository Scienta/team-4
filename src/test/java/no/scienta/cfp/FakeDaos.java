package no.scienta.cfp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;

/**
 * In-memory stand-ins for the DAOs, for unit tests of {@link ConferenceService} without a database. Each fake overrides
 * every public method of its DAO, so the JdbcTemplate (null) is never touched. They model the database rules the
 * service relies on: unique email in any case, the 3-talk limit, one rating per reviewer, co-speakers in order.
 */
final class FakeDaos {

    final FakeSpeakerDao speakers = new FakeSpeakerDao();
    final FakeTalkDao talks = new FakeTalkDao();
    final FakeRatingDao ratings = new FakeRatingDao();
    final FakeCoSpeakerDao coSpeakers = new FakeCoSpeakerDao();

    ConferenceService service() {
        return new ConferenceService(speakers, talks, ratings, coSpeakers, new NotificationService());
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
                Map<String, Object> row = rows.get(id);
                if (row != null) {
                    names.put(id, (String) row.get("name"));
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

        private final Map<Long, TalkDto> rows = new LinkedHashMap<>();

        FakeTalkDao() {
            super(null);
        }

        @Override
        public TalkDto findById(long id) {
            TalkDto t = rows.get(id);
            return t == null ? null : copy(t);
        }

        @Override
        public List<TalkDto> findBySpeaker(long speakerId) {
            return rows.values().stream().filter(t -> t.speakerId == speakerId).map(FakeTalkDao::copy)
                    .collect(Collectors.toList());
        }

        @Override
        public List<TalkDto> findAll() {
            return rows.values().stream().map(FakeTalkDao::copy).collect(Collectors.toList());
        }

        @Override
        public Long lockMainSpeaker(long id) {
            TalkDto t = rows.get(id);
            return t == null ? null : t.speakerId;
        }

        @Override
        public boolean exists(long id) {
            return rows.containsKey(id);
        }

        @Override
        public Long insertIfBelow(int max, String title, String format, int duration, String level, long speakerId,
                                  long submittedAt) {
            if (findBySpeaker(speakerId).size() >= max) {
                return null;
            }
            TalkDto t = new TalkDto();
            t.id = (long) rows.size() + 1;
            t.title = title;
            t.format = format;
            t.durationMinutes = duration;
            t.level = level;
            t.status = "SUBMITTED";
            t.speakerId = speakerId;
            t.submittedAt = new Date(submittedAt);
            t.submittedOn = Utils.toSqlDate(submittedAt);
            rows.put(t.id, t);
            return t.id;
        }

        private static TalkDto copy(TalkDto s) {
            TalkDto t = new TalkDto();
            t.id = s.id;
            t.title = s.title;
            t.format = s.format;
            t.durationMinutes = s.durationMinutes;
            t.level = s.level;
            t.status = s.status;
            t.speakerId = s.speakerId;
            t.submittedAt = s.submittedAt;
            t.submittedOn = s.submittedOn;
            return t;
        }
    }

    static class FakeRatingDao extends RatingDao {

        /** talkId -> reviewerId -> score; a reviewer's new rating replaces the old one. */
        private final Map<Long, Map<Long, Integer>> scores = new LinkedHashMap<>();

        FakeRatingDao() {
            super(null);
        }

        @Override
        public void save(long talkId, long reviewerId, int score, String ratedAt) {
            scores.computeIfAbsent(talkId, k -> new LinkedHashMap<>()).put(reviewerId, score);
        }

        @Override
        public Map<Long, Stats> stats(Collection<Long> talkIds) {
            Map<Long, Stats> stats = new LinkedHashMap<>();
            for (Long talkId : talkIds) {
                Map<Long, Integer> forTalk = scores.get(talkId);
                if (forTalk != null && !forTalk.isEmpty()) {
                    stats.put(talkId, new Stats(forTalk.size(), forTalk.values().stream().mapToLong(Integer::longValue).sum()));
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
