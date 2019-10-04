package uk.gov.justice.hmpps.offenderevents.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class OAuthApiHealth extends HealthCheck {

    @Autowired
    public OAuthApiHealth(@Qualifier("oauthApiRestTemplate") final RestTemplate restTemplate) {
        super(restTemplate);
    }
}
