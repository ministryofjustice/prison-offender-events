package uk.gov.justice.hmpps.offenderevents.services;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
@Slf4j
public class EventRetrievalService {

    private final ExternalApiService externalApiService;
    private final SnsService snsService;

    public void pollEvents(final LocalDateTime eventsFromDateTime) {
        final var events = externalApiService.getEvents(eventsFromDateTime);
        log.debug("There are {} events [{}]", events.size(), events);
        events.parallelStream().forEach(snsService::sendEvent);
    }
}
