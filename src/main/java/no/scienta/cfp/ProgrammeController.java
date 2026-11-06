package no.scienta.cfp;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProgrammeController {

    private final ProgrammeService service;

    public ProgrammeController(ProgrammeService service) {
        this.service = service;
    }

    @GetMapping("/programme")
    public List<ProgrammeRow> export() {
        return service.export();
    }

    /**
     * text/csv whatever the client accepts: the Content-Type is set on the response itself, so Spring writes it
     * as is instead of negotiating (an Accept: application/json would otherwise get a 406).
     */
    @GetMapping("/programme.csv")
    public ResponseEntity<String> exportCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(ProgrammeCsv.write(service.export()));
    }

    @PutMapping("/programme/{room}/{slot}")
    public ProgrammeEntryView put(@PathVariable String room, @PathVariable String slot,
                                  @RequestBody(required = false) Map<String, Object> body) {
        return service.put(room, slot, body);
    }

    @GetMapping("/programme/{room}/{slot}")
    public ProgrammeEntryView get(@PathVariable String room, @PathVariable String slot) {
        return service.get(room, slot);
    }

    @DeleteMapping("/programme/{room}/{slot}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String room, @PathVariable String slot) {
        service.delete(room, slot);
    }
}
