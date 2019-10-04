package uk.gov.justice.hmpps.offenderevents.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class Elite2ApiHealth extends HealthCheck {

    @Autowired
    public Elite2ApiHealth(@Qualifier("elite2ApiRestTemplate") final RestTemplate restTemplate) {
        super(restTemplate);
    }
}
