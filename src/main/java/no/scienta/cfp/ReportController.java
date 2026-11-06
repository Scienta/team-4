package no.scienta.cfp;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exports for the programme committee's spreadsheet. Not part of the public API.
 */
@RestController
public class ReportController {

    private final StatisticsService statistics;
    private final TalkService talkService;
    private final SpeakerService speakerService;

    public ReportController(StatisticsService statistics, TalkService talkService, SpeakerService speakerService) {
        this.statistics = statistics;
        this.talkService = talkService;
        this.speakerService = speakerService;
    }

    @GetMapping("/admin/stats")
    public Map<String, Object> stats() {
        return statistics.statistics();
    }

    @GetMapping("/admin/talks.csv")
    public ResponseEntity<String> talksCsv() {
        List<TalkView> talks = talkService.listTalks(null, null);
        Map<Long, String> speakerNames = speakerService.getSpeakerNames(talks.stream().map(TalkView::speakerId).toList());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=talks.csv")
                .body(TalksCsv.write(talks, speakerNames));
    }
}
