package uk.gov.justice.hmpps.offenderevents.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;
import uk.gov.justice.hmpps.offenderevents.model.PollAudit;
import uk.gov.justice.hmpps.offenderevents.repository.PollAuditRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

import static java.time.temporal.ChronoUnit.MICROS;
import static java.time.temporal.ChronoUnit.MILLIS;

@Service
@Slf4j
@Transactional
public class EventRetrievalService {

    public static final String POLL_NAME = "offenderEvents";

    private final ExternalApiService externalApiService;
    private final PrisonEventsEmitter prisonEventsEmitter;
    private final PollAuditRepository repository;
    private final HMPPSDomainEventsEmitter hmppsDomainEventsEmitter;
    private final int pollInterval;
    private final int maxEventRangeMinutes;
    private final int windBackSeconds;


    public EventRetrievalService(final ExternalApiService externalApiService,
                                 final PrisonEventsEmitter prisonEventsEmitter,
                                 final HMPPSDomainEventsEmitter hmppsDomainEventsEmitter,
                                 final PollAuditRepository repository,
                                 @Value("${application.events.poll.frequency:60000}") final int pollInterval,
                                 @Value("${wind.back.seconds:10}") int windBackSeconds,
                                 @Value("${application.events.max.range.minutes:20}") final int maxEventRangeMinutes) {
        this.externalApiService = externalApiService;
        this.prisonEventsEmitter = prisonEventsEmitter;
        this.repository = repository;
        this.pollInterval = pollInterval;
        this.maxEventRangeMinutes = maxEventRangeMinutes;
        this.windBackSeconds = windBackSeconds;
        log.info("Using {} wind back seconds", windBackSeconds);
        this.hmppsDomainEventsEmitter = hmppsDomainEventsEmitter;
    }

    public void pollEvents(final LocalDateTime currentTime) {

        final var latestPollTimeAllowed = currentTime.minusSeconds(windBackSeconds);
        final var audit = repository.findById(POLL_NAME).orElse(
                PollAudit.builder()
                        .pollName(POLL_NAME)
                        .nextStartTime(latestPollTimeAllowed.minus(pollInterval, MILLIS))
                        .build());
        repository.save(audit);

        final var startTime = audit.getNextStartTime();
        final var timeDifferenceMins = startTime.until(latestPollTimeAllowed, ChronoUnit.MINUTES);
        final var endTime = timeDifferenceMins > maxEventRangeMinutes ? startTime.plusMinutes(maxEventRangeMinutes) : latestPollTimeAllowed;

        if (startTime.compareTo(endTime) < 0) {  // This is just to handle if end the start time is before end due to wind bank seconds
            log.debug("Getting events between {} and {}", startTime, endTime);
            final var events = externalApiService.getEvents(startTime, endTime);
            log.debug("There are {} events {}", events.size(), events);
            events.forEach(prisonEventsEmitter::sendEvent);
            events.forEach(this::tryHMPPSDomainEventsEmitter);

            final var lastEventTime = events.stream()
                    .max(Comparator.comparing(OffenderEvent::getEventDatetime))
                    .orElse(OffenderEvent.builder().eventDatetime(latestPollTimeAllowed).build())
                    .getEventDatetime();

            audit.setNumberOfRecords(events.size());
            audit.setNextStartTime(lastEventTime.plus(1, MICROS)); // add 1 micro sec to last record.
            repository.save(audit);

            log.debug("Recording Event Poll {}", audit);
        } else {
            log.warn("Skipping Event Retrieval as start after end, start = {}, end = {}", startTime, endTime);
        }
    }

    private void tryHMPPSDomainEventsEmitter(OffenderEvent event) {
        // we have no reasonable way to recover from these errors so swallow and log
        try {
            hmppsDomainEventsEmitter.convertAndSendWhenSignificant(event);
        } catch (Exception e) {
            log.error(String.format("Unable to process a HMPPS domain event for %s", event), e);
        }
    }
}
