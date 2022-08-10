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
import static java.time.temporal.ChronoUnit.MINUTES;

@Service
@Slf4j
@Transactional
public class EventRetrievalService {

    public static final String POLL_NAME = "offenderEvents";
    public static final String POLL_NAME_TEST = "offenderEvents-test";
    public static final String PREVIOUS_POLL_NAME_TEST = "offenderEvents-test-previous";

    private final ExternalApiService externalApiService;
    private final PrisonEventsEmitter prisonEventsEmitter;
    private final PollAuditRepository repository;
    private final int pollInterval;
    private final int maxEventRangeMinutes;
    private final int windBackSeconds;
    private final int testWindBackSeconds;


    public EventRetrievalService(final ExternalApiService externalApiService,
                                 final PrisonEventsEmitter prisonEventsEmitter,
                                 final PollAuditRepository repository,
                                 @Value("${application.events.poll.frequency:60000}") final int pollInterval,
                                 @Value("${wind.back.seconds:10}") int windBackSeconds,
                                 @Value("${test.wind.back.seconds:10}") int testWindBackSeconds,
                                 @Value("${application.events.max.range.minutes:20}") final int maxEventRangeMinutes) {
        this.externalApiService = externalApiService;
        this.prisonEventsEmitter = prisonEventsEmitter;
        this.repository = repository;
        this.pollInterval = pollInterval;
        this.maxEventRangeMinutes = maxEventRangeMinutes;
        this.windBackSeconds = windBackSeconds;
        this.testWindBackSeconds = testWindBackSeconds;
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
        final var timeDifferenceMins = startTime.until(latestPollTimeAllowed, ChronoUnit.MINUTES);
        final var endTime = timeDifferenceMins > maxEventRangeMinutes ? startTime.plusMinutes(maxEventRangeMinutes) : latestPollTimeAllowed;

        if (startTime.compareTo(endTime) < 0) {  // This is just to handle if end the start time is before end due to wind bank seconds
            log.debug("Getting events between {} and {}", startTime, endTime);
            final var events = externalApiService.getEvents(startTime, endTime);
            log.debug("There are {} events {}", events.size(), events);
            events.forEach(prisonEventsEmitter::sendEvent);

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

    /**
     * For testing run a poll for the immediately ended minute, repeat 1 min later and compare:
     * <ol>
     *  <li>run for the latest minute (POLL_NAME_TEST.time -> now) remembering time range and count, A
     *  <li>run for [PREVIOUS_POLL_NAME_TEST -> POLL_NAME_TEST] time range remembering time range and count, B
     *  <li>compare count B with POLL_NAME_TEST.count - should be the same
     *  <li>store count A in POLL_NAME_TEST.count
     *  <li>move POLL_NAME_TEST.time to PREVIOUS_POLL_NAME_TEST.time
     *  <li>store A end time (now) in POLL_NAME_TEST.time
     * </ol>
     */
    public void runTestPolls(final LocalDateTime now) {
        final LocalDateTime endTimeA = now.minusSeconds(testWindBackSeconds);
        final boolean useEnq = false;

        repository.findById(POLL_NAME_TEST).ifPresentOrElse(
                test -> {
                    final var previousTest = repository.findById(PREVIOUS_POLL_NAME_TEST).orElseThrow();
                    final var eventsA = externalApiService.getTestEvents(test.getNextStartTime(), endTimeA, useEnq);
                    final var countA = eventsA.size();
                    log.debug("runTestPolls(): A interval {} to {}, count {}", test.getNextStartTime(), endTimeA, countA);

                    final var eventsB = externalApiService.getTestEvents(previousTest.getNextStartTime(), test.getNextStartTime(), useEnq);
                    final var countB = eventsB.size();
                    log.debug("runTestPolls(): B interval {} to {}, count {}", previousTest.getNextStartTime(), test.getNextStartTime(), countB);

                    if (countB != test.getNumberOfRecords()) {
                        log.warn("runTestPolls(): Found different counts using windback {}, useEnq {}, original={}, repeat={}, events {}",
                                testWindBackSeconds, useEnq, test.getNumberOfRecords(), countB, eventsB);
                    }

                    test.setNumberOfRecords(countA);
                    previousTest.setNextStartTime(test.getNextStartTime());
                    test.setNextStartTime(endTimeA);
                },
                () -> {
                    // First run
                    final LocalDateTime startTimeA = endTimeA.minus(1, MINUTES);
                    final var eventsA = externalApiService.getTestEvents(startTimeA, endTimeA, useEnq);
                    final var countA = eventsA.size();

                    repository.save(PollAudit.builder()
                            .pollName(POLL_NAME_TEST)
                            .nextStartTime(endTimeA)
                            .numberOfRecords(countA)
                            .build());
                    repository.save(PollAudit.builder()
                            .pollName(PREVIOUS_POLL_NAME_TEST)
                            .nextStartTime(startTimeA)
                            .build());
                }
        );
    }
}
