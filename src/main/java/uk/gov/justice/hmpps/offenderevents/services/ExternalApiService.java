package uk.gov.justice.hmpps.offenderevents.services;

import lombok.AllArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class ExternalApiService {

    private final OAuth2RestTemplate custodyapiRestTemplate;

    public List<OffenderEvent> getEvents(final LocalDateTime fromDateTime, final LocalDateTime toDateTime) {
        final var uri = getEventUriBuilder(fromDateTime, toDateTime).build().toUri();
        final var response = custodyapiRestTemplate.exchange(uri, HttpMethod.GET, null, new ParameterizedTypeReference<List<OffenderEvent>>() {
        });
        return response.getBody();
    }

    private UriComponentsBuilder getEventUriBuilder(final LocalDateTime fromDateTime, final LocalDateTime toDateTime) {
        // bit naff, but the template handler holds the root uri that we need, so have to create a uri from it to then pass to the builder

        final var uri = custodyapiRestTemplate.getUriTemplateHandler().expand("/events").normalize();
        return UriComponentsBuilder.fromUri(uri)
                .queryParam("sortBy", "TIMESTAMP_ASC")
                .queryParam("from", fromDateTime)
                .queryParam("to", toDateTime);
    }

}

