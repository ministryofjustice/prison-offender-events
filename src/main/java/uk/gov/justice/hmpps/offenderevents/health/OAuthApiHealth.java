package uk.gov.justice.hmpps.offenderevents.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.justice.hmpps.offenderevents.config.OffenderEventsProperties;

import java.time.Duration;

@Component
public class OAuthApiHealth extends HealthCheck {

    @Autowired
    public OAuthApiHealth(final WebClient webClient, final OffenderEventsProperties properties, @Value("${api.health-timeout:1s}") final Duration healthTimeout) {
        super(webClient, properties.getOauthApiBaseUrl(), healthTimeout);
    }
}
