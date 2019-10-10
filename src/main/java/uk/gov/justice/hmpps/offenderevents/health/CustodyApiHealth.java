package uk.gov.justice.hmpps.offenderevents.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class CustodyApiHealth extends HealthCheck {

    @Autowired
    public CustodyApiHealth(@Qualifier("custodyapiHealthRestTemplate") final RestTemplate restTemplate) {
        super(restTemplate);
    }
}
