package no.scienta.cfp;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Serves the front page at {@code /} directly. Spring Boot's welcome page forwards {@code /} to {@code /index.html},
 * and Tomcat closes the response at the end of a forward, before {@link RequestLoggingFilter}'s caching wrapper has
 * copied the body out: the client got 200 with an empty body.
 */
@Controller
class FrontPageController {

    private static final Resource INDEX = new ClassPathResource("static/index.html");

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    Resource index() {
        return INDEX;
    }
}
