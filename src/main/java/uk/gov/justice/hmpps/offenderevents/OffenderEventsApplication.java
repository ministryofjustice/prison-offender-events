package uk.gov.justice.hmpps.offenderevents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;

@SpringBootApplication
@EnableResourceServer
public class OffenderEventsApplication {
    public static void main(final String[] args) {
        SpringApplication.run(OffenderEventsApplication.class, args);
    }

}
