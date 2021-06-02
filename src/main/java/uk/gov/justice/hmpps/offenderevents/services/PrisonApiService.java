package uk.gov.justice.hmpps.offenderevents.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Service
@Slf4j
public class PrisonApiService {

    private final WebClient oauth2WebClient;
    private final Duration timeout;

    public PrisonApiService(final WebClient oauth2WebClient, @Value("${api.prisoner-timeout:30s}") final Duration timeout) {
        this.oauth2WebClient = oauth2WebClient;
        this.timeout = timeout;
    }

    public PrisonerDetails getPrisonerDetails(final String offenderNumber) {
        return oauth2WebClient.get()
            .uri(String.format("/api/offenders/%s", offenderNumber))
            .retrieve()
            .bodyToMono(PrisonerDetails.class)
            .block(timeout);
    }

}

record PrisonerDetails(LegalStatus legalStatus, boolean recall) {
}


enum LegalStatus {
    RECALL,
    DEAD,
    INDETERMINATE_SENTENCE,
    SENTENCED,
    CONVICTED_UNSENTENCED,
    CIVIL_PRISONER,
    IMMIGRATION_DETAINEE,
    REMAND,
    UNKNOWN,
    OTHER;
}
