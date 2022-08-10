package uk.gov.justice.hmpps.offenderevents.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ExternalApiService {

    private final WebClient oauth2WebClient;
    private final Duration timeout;

    public ExternalApiService(final WebClient prisonApiWebClient, @Value("${api.event-timeout:60s}") final Duration timeout) {
        this.oauth2WebClient = prisonApiWebClient;
        this.timeout = timeout;
    }

    public List<OffenderEvent> getEvents(final LocalDateTime fromDateTime, final LocalDateTime toDateTime) {
        final var uri = getEventUriBuilder(fromDateTime, toDateTime).build().toString();
        return oauth2WebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<OffenderEvent>>() {})
                .block(timeout);
    }


    public List<OffenderEvent> getTestEvents(final LocalDateTime fromDateTime, final LocalDateTime toDateTime, final boolean useEnq) {
        final var uri = getTestEventUriBuilder(fromDateTime, toDateTime, useEnq).build().toString();
        return oauth2WebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<OffenderEvent>>() {})
                .block(timeout);
    }

    private UriComponentsBuilder getEventUriBuilder(final LocalDateTime fromDateTime, final LocalDateTime toDateTime) {
        return UriComponentsBuilder.fromUriString("/api/events")
                .queryParam("sortBy", "TIMESTAMP_ASC")
                .queryParam("from", fromDateTime)
                .queryParam("to", toDateTime);
    }

    private UriComponentsBuilder getTestEventUriBuilder(final LocalDateTime fromDateTime, final LocalDateTime toDateTime, final boolean useEnq) {
        return UriComponentsBuilder.fromUriString("/api/test-events")
                .queryParam("useEnq", useEnq)
                .queryParam("from", fromDateTime)
                .queryParam("to", toDateTime);
    }
}

