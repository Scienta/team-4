package no.scienta.cfp;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

/** The programme committee's figures for /admin/stats. */
@Service
public class StatisticsService {

    private final SpeakerDao speakerDao;
    private final TalkService talkService;

    public StatisticsService(SpeakerDao speakerDao, TalkService talkService) {
        this.speakerDao = speakerDao;
        this.talkService = talkService;
    }

    public Map<String, Object> statistics() {
        List<TalkDto> talks = talkService.listTalks(null, null);
        Map<String, Integer> byFormat = new LinkedHashMap<>();
        Map<String, Integer> byLevel = new LinkedHashMap<>();
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        Set<Long> speakersWithTalks = new HashSet<>();
        int minutes = 0;
        double ratingSum = 0;
        int rated = 0;
        for (TalkDto t : talks) {
            byFormat.put(t.format, byFormat.getOrDefault(t.format, 0) + 1);
            byLevel.put(t.level, byLevel.getOrDefault(t.level, 0) + 1);
            byStatus.put(t.status, byStatus.getOrDefault(t.status, 0) + 1);
            speakersWithTalks.add(t.speakerId);
            minutes += t.durationMinutes;
            if (t.averageRating != null) {
                ratingSum += t.averageRating;
                rated++;
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("speakers", speakerDao.count());
        stats.put("speakersWithTalks", speakerDao.names(speakersWithTalks).size()); // only speakers that still exist
        stats.put("talks", talks.size());
        stats.put("byFormat", byFormat);
        stats.put("byLevel", byLevel);
        stats.put("byStatus", byStatus);
        stats.put("totalHours", minutes / 60);
        stats.put("averageRating", rated == 0 ? null : Utils.roundRating(ratingSum / rated));
        stats.put("generated", Utils.now());
        return stats;
    }
}
