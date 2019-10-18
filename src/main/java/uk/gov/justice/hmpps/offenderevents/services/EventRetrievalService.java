package uk.gov.justice.hmpps.offenderevents.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;
import uk.gov.justice.hmpps.offenderevents.model.PollAudit;
import uk.gov.justice.hmpps.offenderevents.repository.PollAuditRepository;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;

import static java.time.LocalDateTime.now;
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


    public EventRetrievalService(final ExternalApiService externalApiService,
                                 final SnsService snsService,
                                 final PollAuditRepository repository,
                                 @Value("${application.events.poll.frequency:60000}") final int pollInterval,
                                 @Value("${application.events.max.range.hours:1}") final int maxEventRangeHours) {
        this.externalApiService = externalApiService;
        this.snsService = snsService;
        this.repository = repository;
        this.pollInterval = pollInterval;
        this.maxEventRangeHours = maxEventRangeHours;
    }

    public void pollEvents() {

        final var currentTime = now();
        final var audit = repository.findById(POLL_NAME).orElse(PollAudit.builder().pollName(POLL_NAME).nextRunTime(currentTime.minus(pollInterval, MILLIS)).build());
        repository.save(audit);

        final var timeDifferenceHrs = audit.getNextRunTime().until(currentTime, ChronoUnit.HOURS);
        final var endTime = timeDifferenceHrs > maxEventRangeHours ? audit.getNextRunTime().plusHours(maxEventRangeHours) : currentTime;

        log.debug("Getting events between {} and {}", audit.getNextRunTime(), endTime);
        final var events = externalApiService.getEvents(audit.getNextRunTime(), endTime);
        log.debug("There are {} events {}", events.size(), events);

        events.forEach(snsService::sendEvent);

        final var lastEventTime = events.stream()
                .max(Comparator.comparing(OffenderEvent::getEventDatetime))
                .orElse(OffenderEvent.builder().eventDatetime(currentTime).build())
                .getEventDatetime();

        audit.setNumberOfRecords(events.size());
        audit.setNextRunTime(lastEventTime.plus(1, MICROS)); // add 1 micro sec to last record.
        repository.save(audit);

        log.debug("Recording Event Poll {}", audit);
    }
}
