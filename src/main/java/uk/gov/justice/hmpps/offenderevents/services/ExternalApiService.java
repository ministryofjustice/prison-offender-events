package uk.gov.justice.hmpps.offenderevents.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.justice.hmpps.offenderevents.config.OffenderEventsProperties;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId;

@Service
@Slf4j
public class ExternalApiService {

    private final OffenderEventsProperties properties;
    private final WebClient oauth2WebClient;
    private final Duration timeout;

    public ExternalApiService(final OffenderEventsProperties properties, final WebClient oauth2WebClient, @Value("${api.event-timeout:30s}") final Duration timeout) {
        this.properties = properties;
        this.oauth2WebClient = oauth2WebClient;
        this.timeout = timeout;
    }

    public List<OffenderEvent> getEvents(final LocalDateTime fromDateTime, final LocalDateTime toDateTime) {
        final var uri = getEventUriBuilder(fromDateTime, toDateTime).build().toUri();
        return oauth2WebClient.get()
                .uri(uri)
                .attributes(clientRegistrationId("custody-api"))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<OffenderEvent>>() {})
                .block(timeout);
    }

    private UriComponentsBuilder getEventUriBuilder(final LocalDateTime fromDateTime, final LocalDateTime toDateTime) {
        return UriComponentsBuilder.fromUriString(properties.getCustodyApiBaseUrl() + "/api/events")
                .queryParam("sortBy", "TIMESTAMP_ASC")
                .queryParam("from", fromDateTime)
                .queryParam("to", toDateTime);
    }

}

