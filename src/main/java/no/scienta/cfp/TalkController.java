package no.scienta.cfp;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TalkController {

    private final TalkService talks;
    private final CoSpeakerService coSpeakers;
    private final AcceptanceService acceptance;
    private final RatingService ratings;

    public TalkController(TalkService talks, CoSpeakerService coSpeakers, AcceptanceService acceptance,
                          RatingService ratings) {
        this.talks = talks;
        this.coSpeakers = coSpeakers;
        this.acceptance = acceptance;
        this.ratings = ratings;
    }

    @PostMapping("/talks")
    @ResponseStatus(HttpStatus.CREATED)
    public TalkDto submit(@RequestBody TalkForm form) {
        return talks.submitTalk(form);
    }

    @GetMapping("/talks/{id}")
    public TalkDto get(@PathVariable Long id) {
        return talks.getTalk(id);
    }

    @GetMapping("/talks")
    public List<TalkDto> list(@RequestParam(required = false) String sort,
                              @RequestParam(required = false) Long speakerId) {
        return talks.listTalks(sort, speakerId);
    }

    @PutMapping("/talks/{id}/co-speakers/{speakerId}")
    public TalkDto addCoSpeaker(@PathVariable Long id, @PathVariable Long speakerId) {
        return coSpeakers.addCoSpeaker(id, speakerId);
    }

    @DeleteMapping("/talks/{id}/co-speakers/{speakerId}")
    public TalkDto removeCoSpeaker(@PathVariable Long id, @PathVariable Long speakerId) {
        return coSpeakers.removeCoSpeaker(id, speakerId);
    }

    @PutMapping("/talks/{id}/status")
    public TalkDto decide(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return acceptance.decide(id, body);
    }

    @PostMapping("/talks/{id}/withdraw")
    public TalkDto withdraw(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return acceptance.withdraw(id, body);
    }

    @PutMapping("/talks/{id}/ratings/{reviewerId}")
    public Rating rate(@PathVariable Long id, @PathVariable Long reviewerId,
                       @RequestBody Map<String, Object> body) {
        return ratings.rate(id, reviewerId, body);
    }
}
