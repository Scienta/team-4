package no.scienta.cfp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CfpApplication {

    public static void main(String[] args) {
        // No default time zone: the reporting server ran in Oslo time once and everything was off by an hour.
        SpringApplication.run(CfpApplication.class, args);
    }
}
