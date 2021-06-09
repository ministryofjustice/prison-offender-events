package uk.gov.justice.hmpps.offenderevents.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Slf4j
public class CommunityApiService {

    private final WebClient webClient;
    private final Duration timeout;

    public CommunityApiService(final WebClient communityApiWebClient, @Value("${api.community-timeout:30s}") final Duration timeout) {
        this.webClient = communityApiWebClient;
        this.timeout = timeout;
    }

    public Optional<List<Recall>> getRecalls(final String offenderNumber) {
        return Optional.ofNullable(webClient.get()
            .uri(String.format("/secure/offenders/nomsNumber/%s/convictions/active/nsis/recall", offenderNumber))
            .retrieve()
            .bodyToMono(NsiWrapper.class)
            .onErrorResume(WebClientResponseException.class, this::emptyWhenNotFound)
            .block(timeout))
            .map(NsiWrapper::nsis);
    }

    private <T> Mono<T> emptyWhenNotFound(WebClientResponseException exception) {
        return emptyWhen(exception, NOT_FOUND);
    }

    private <T> Mono<T> emptyWhen(WebClientResponseException exception, @SuppressWarnings("SameParameterValue") HttpStatus statusCode) {
        if (exception.getRawStatusCode() == statusCode.value()) {
            return Mono.empty();
        } else {
            return Mono.error(exception);
        }
    }
}

record NsiWrapper(List<Recall> nsis) {

}

record Recall(LocalDate referralDate) {

}
