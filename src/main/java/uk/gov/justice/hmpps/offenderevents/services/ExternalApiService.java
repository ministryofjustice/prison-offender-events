package uk.gov.justice.hmpps.offenderevents.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.justice.hmpps.offenderevents.config.OffenderEventsProperties;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId;

@Service
@AllArgsConstructor
@Slf4j
public class ExternalApiService {

    private final OffenderEventsProperties properties;
    private final WebClient oauth2WebClient;

    public List<OffenderEvent> getEvents(final LocalDateTime fromDateTime, final LocalDateTime toDateTime) {
        final var uri = getEventUriBuilder(fromDateTime, toDateTime).build().toUri();
        try {
            return oauth2WebClient.get()
                    .uri(uri)
                    .attributes(clientRegistrationId("custody-api"))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<OffenderEvent>>() {
                    })
                    .doOnError(error -> log.error("Error occurred", error))
                    .block();
        } catch (Exception e) {
            return List.of();
        }
    }

    private UriComponentsBuilder getEventUriBuilder(final LocalDateTime fromDateTime, final LocalDateTime toDateTime) {
        // bit naff, but the template handler holds the root uri that we need, so have to create a uri from it to then pass to the builder

        return UriComponentsBuilder.fromUriString(properties.getCustodyApiBaseUrl() + "/api/events")
                .queryParam("sortBy", "TIMESTAMP_ASC")
                .queryParam("from", fromDateTime)
                .queryParam("to", toDateTime);
    }

}

