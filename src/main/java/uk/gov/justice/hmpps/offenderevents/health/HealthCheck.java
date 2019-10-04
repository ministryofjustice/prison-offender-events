package uk.gov.justice.hmpps.offenderevents.health;


import lombok.AllArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static lombok.AccessLevel.PROTECTED;


@AllArgsConstructor(access = PROTECTED)
public abstract class HealthCheck implements HealthIndicator {
    private final RestTemplate restTemplate;

    @Override
    public Health health() {
        try {
            final var responseEntity = this.restTemplate.getForEntity("/ping", String.class);
            return Health.up().withDetail("HttpStatus", responseEntity.getStatusCode()).build();
        } catch (final RestClientException e) {
            return Health.down(e).build();
        }
    }
}
