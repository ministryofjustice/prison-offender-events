package uk.gov.justice.hmpps.offenderevents.health;


import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static lombok.AccessLevel.PROTECTED;


@AllArgsConstructor(access = PROTECTED)
@Slf4j
public abstract class HealthCheck implements HealthIndicator {
    private final WebClient webClient;
    private final String baseUri;
    private final Duration timeout;

    @Override
    public Health health() {
        try {
            final var uri = baseUri + "/ping";
            final var response = webClient.get().uri(uri, String.class)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofMillis(timeout.toMillis()))
                    .block();
            return Health.up().withDetail("HttpStatus", response.getStatusCode()).build();
        } catch (final Exception e) {
            return Health.down(e).build();
        }
    }
}
