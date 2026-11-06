package no.scienta.cfp;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/speakers")
public class SpeakerController {

    private final SpeakerService speakers;
    private final TalkService talks;

    public SpeakerController(SpeakerService speakers, TalkService talks) {
        this.speakers = speakers;
        this.talks = talks;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Speaker create(@Valid @RequestBody SpeakerForm form) {
        return speakers.registerSpeaker(form);
    }

    @GetMapping("/{id}")
    public Speaker get(@PathVariable Long id) {
        return speakers.getSpeaker(id);
    }

    // used by the admin UI to fix typos. No @Valid: the service checks the rules, as it did for the map before.
    @PutMapping("/{id}")
    public Speaker update(@PathVariable("id") Long id, @RequestBody SpeakerForm form) {
        return speakers.updateSpeaker(id, form.getName(), form.getEmail(), form.getBio());
    }

    @GetMapping("/{id}/talks")
    public List<TalkView> talks(@PathVariable Long id) {
        return talks.getTalksForSpeaker(id);
    }

    // admin only, not in the API docs
    @GetMapping("/search")
    public List<Speaker> search(@RequestParam String q) {
        return speakers.searchSpeakers(q);
    }
}
