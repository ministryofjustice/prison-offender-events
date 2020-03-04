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
    private final SnsService snsService;
    private final PollAuditRepository repository;
    private final int pollInterval;
    private final int maxEventRangeHours;
    private final int windBackSeconds;


    public EventRetrievalService(final ExternalApiService externalApiService,
                                 final SnsService snsService,
                                 final PollAuditRepository repository,
                                 @Value("${application.events.poll.frequency:60000}") final int pollInterval,
                                 @Value("${wind.back.seconds:10}") int windBackSeconds,
                                 @Value("${application.events.max.range.hours:1}") final int maxEventRangeHours) {
        this.externalApiService = externalApiService;
        this.snsService = snsService;
        this.repository = repository;
        this.pollInterval = pollInterval;
        this.maxEventRangeHours = maxEventRangeHours;
        this.windBackSeconds = windBackSeconds;
        log.info("Using {} wind back seconds", windBackSeconds);
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
        final var timeDifferenceHrs = startTime.until(latestPollTimeAllowed, ChronoUnit.HOURS);
        final var endTime = timeDifferenceHrs > maxEventRangeHours ? startTime.plusHours(maxEventRangeHours) : latestPollTimeAllowed;

        if (startTime.compareTo(endTime) < 0) {  // This is just to handle if end the start time is before end due to wind bank seconds
            log.debug("Getting events between {} and {}", startTime, endTime);
            final var events = externalApiService.getEvents(startTime, endTime);
            log.debug("There are {} events {}", events.size(), events);
            events.forEach(snsService::sendEvent);

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
}
