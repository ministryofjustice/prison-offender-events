package uk.gov.justice.hmpps.offenderevents.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;
import uk.gov.justice.hmpps.offenderevents.model.PollAudit;
import uk.gov.justice.hmpps.offenderevents.repository.PollAuditRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.MICROS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.hmpps.offenderevents.services.EventRetrievalService.POLL_NAME;
import static uk.gov.justice.hmpps.offenderevents.services.EventRetrievalService.POLL_NAME_TEST;
import static uk.gov.justice.hmpps.offenderevents.services.EventRetrievalService.PREVIOUS_POLL_NAME_TEST;

@ExtendWith(MockitoExtension.class)
public class EventRetrievalServiceTest {

    public static final int WIND_BACK_SECONDS = 400;
    public static final int POLL_INTERVAL = 60000;

    @Mock
    private ExternalApiService externalApiService;
    @Mock
    private PrisonEventsEmitter prisonEventsEmitter;
    @Mock
    private PollAuditRepository repository;

    private final int maxEventRangeMinutes = 60;

    private EventRetrievalService eventRetrievalService;

    @BeforeEach
    public void setup() {
        eventRetrievalService = new EventRetrievalService(externalApiService, prisonEventsEmitter, repository, POLL_INTERVAL, WIND_BACK_SECONDS, 5, maxEventRangeMinutes);
    }

    @Test
    public void testEventRetrievalNeverRunBefore() {

        final var now = LocalDateTime.now();
        final var windBackPoint = now.minusSeconds(WIND_BACK_SECONDS);
        final var eventTimeStart = windBackPoint.minusMinutes(1);

        when(repository.findById(eq(POLL_NAME))).thenReturn(Optional.empty());
        final var events = List.of
                (
                        OffenderEvent.builder().bookingId(-1L).eventDatetime(eventTimeStart.plusSeconds(3)).build(),
                        OffenderEvent.builder().bookingId(-2L).eventDatetime(eventTimeStart.plusSeconds(2)).build(),
                        OffenderEvent.builder().bookingId(-3L).eventDatetime(eventTimeStart.plusSeconds(1)).build()
                );
        when(externalApiService.getEvents(eq(eventTimeStart), eq(windBackPoint))).thenReturn(events);

        prisonEventsEmitter.sendEvent(eq(events.get(2)));
        prisonEventsEmitter.sendEvent(eq(events.get(1)));
        prisonEventsEmitter.sendEvent(eq(events.get(0)));

        final var resultPoll = PollAudit.builder().pollName(POLL_NAME).nextStartTime(events.get(2).getEventDatetime().plus(1, MICROS)).numberOfRecords(3).build();
        repository.save(eq(resultPoll));
        eventRetrievalService.pollEvents(now);

        verify(repository).findById(eq(POLL_NAME));

        verify(externalApiService).getEvents(eq(eventTimeStart), eq(windBackPoint));
        verify(prisonEventsEmitter, times(3)).sendEvent(any(OffenderEvent.class));
    }


    @Test
    public void testEventRetrievalWhereLastRunWasWindBackWindowsAndOneMinute() {

        final var now = LocalDateTime.now();
        final var windBackPoint = now.minusSeconds(WIND_BACK_SECONDS);
        final var lastRun = windBackPoint.minusMinutes(1);

        when(repository.findById(eq(POLL_NAME)))
                .thenReturn(Optional.of(PollAudit.builder().pollName(POLL_NAME).nextStartTime(lastRun).build()));
        final var events = List.of
                (
                        OffenderEvent.builder().bookingId(-1L).eventDatetime(lastRun.plusSeconds(3)).build(),
                        OffenderEvent.builder().bookingId(-2L).eventDatetime(lastRun.plusSeconds(2)).build(),
                        OffenderEvent.builder().bookingId(-3L).eventDatetime(lastRun.plusSeconds(1)).build()
                );
        when(externalApiService.getEvents(eq(lastRun), eq(windBackPoint))).thenReturn(events);

        prisonEventsEmitter.sendEvent(eq(events.get(2)));
        prisonEventsEmitter.sendEvent(eq(events.get(1)));
        prisonEventsEmitter.sendEvent(eq(events.get(0)));

        final var resultPoll = PollAudit.builder().pollName(POLL_NAME).nextStartTime(events.get(2).getEventDatetime().plus(1, MICROS)).numberOfRecords(3).build();
        repository.save(eq(resultPoll));
        eventRetrievalService.pollEvents(now);

        verify(repository).findById(eq(POLL_NAME));
        verify(repository, times(2)).save(any(PollAudit.class));
        verify(externalApiService).getEvents(eq(lastRun), eq(windBackPoint));
        verify(prisonEventsEmitter, times(3)).sendEvent(any(OffenderEvent.class));
    }

    @Test
    public void testEventRetrievalRanOneDayAgo() {
        final var now = LocalDateTime.now();
        final var lastRun = now.minusDays(1);

        when(repository.findById(eq(POLL_NAME)))
                .thenReturn(Optional.of(PollAudit.builder().pollName(POLL_NAME).nextStartTime(lastRun).build()));
        final var events = List.of
                (
                        OffenderEvent.builder().bookingId(-1L).eventDatetime(lastRun.plusMinutes(15)).build(),
                        OffenderEvent.builder().bookingId(-2L).eventDatetime(lastRun.plusMinutes(30)).build(),
                        OffenderEvent.builder().bookingId(-3L).eventDatetime(lastRun.plusMinutes(45)).build(),
                        OffenderEvent.builder().bookingId(-4L).eventDatetime(lastRun.plusMinutes(60)).build()
                );
        when(externalApiService.getEvents(eq(lastRun), eq(lastRun.plusMinutes(maxEventRangeMinutes)))).thenReturn(events);

        prisonEventsEmitter.sendEvent(eq(events.get(3)));
        prisonEventsEmitter.sendEvent(eq(events.get(2)));
        prisonEventsEmitter.sendEvent(eq(events.get(1)));
        prisonEventsEmitter.sendEvent(eq(events.get(0)));

        final var resultPoll = PollAudit.builder().pollName(POLL_NAME).nextStartTime(events.get(2).getEventDatetime().plus(1, MICROS)).numberOfRecords(3).build();
        repository.save(eq(resultPoll));
        eventRetrievalService.pollEvents(now);

        verify(repository).findById(eq(POLL_NAME));
        verify(externalApiService).getEvents(eq(lastRun), eq(lastRun.plusMinutes(maxEventRangeMinutes)));
        verify(prisonEventsEmitter, times(4)).sendEvent(any(OffenderEvent.class));
    }

    @Test
    public void testEventRetrievalRanAMinuteAgoBeforeNewWindBackWindowAdded() {
        final var now = LocalDateTime.now();
        final var lastRun = now.minusMinutes(1);

        final var poller = PollAudit.builder().pollName(POLL_NAME).nextStartTime(lastRun).build();
        when(repository.findById(eq(POLL_NAME))).thenReturn(Optional.of(poller));

        eventRetrievalService.pollEvents(now);

        verify(repository, times(1)).findById(eq(POLL_NAME));
        verify(repository, times(1)).save(eq(poller));
        verifyNoInteractions(externalApiService);
        verifyNoInteractions(prisonEventsEmitter);
    }

    @Test
    public void testEventRetrievalRanJustOverWindBackWindow() {
        final var now = LocalDateTime.now();
        final var windBackPoint = now.minusSeconds(WIND_BACK_SECONDS);
        final var lastRun = windBackPoint.minusSeconds(3);

        final var lastPoll = PollAudit.builder().pollName(POLL_NAME).nextStartTime(lastRun).build();
        when(repository.findById(eq(POLL_NAME))).thenReturn(Optional.of(lastPoll));

        final var events = List.of
                (
                        OffenderEvent.builder().bookingId(-1L).eventDatetime(lastRun).build(),
                        OffenderEvent.builder().bookingId(-2L).eventDatetime(lastRun.plusSeconds(1)).build()
                );
        when(externalApiService.getEvents(eq(lastRun), eq(windBackPoint))).thenReturn(events);

        prisonEventsEmitter.sendEvent(eq(events.get(0)));
        prisonEventsEmitter.sendEvent(eq(events.get(1)));

        PollAudit.builder().pollName(POLL_NAME).nextStartTime(events.get(1).getEventDatetime().plus(1, MICROS)).numberOfRecords(2).build();

        eventRetrievalService.pollEvents(now);

        verify(repository, times(1)).findById(eq(POLL_NAME));
        verify(externalApiService).getEvents(eq(lastRun), eq(windBackPoint));
        verify(prisonEventsEmitter, times(2)).sendEvent(any(OffenderEvent.class));
    }

    @Test
    public void testRunTestPolls() {
        final var now = LocalDateTime.parse("2022-08-01T12:01:05");
        LocalDateTime startA = LocalDateTime.parse("2022-08-01T12:00:00");
        LocalDateTime endA = LocalDateTime.parse("2022-08-01T12:01:00");
        LocalDateTime startB = LocalDateTime.parse("2022-08-01T11:59:00");
        LocalDateTime endB = startA;

        PollAudit auditA = PollAudit.builder().pollName(POLL_NAME_TEST).nextStartTime(startA).numberOfRecords(2).build();
        when(repository.findById(eq(POLL_NAME_TEST))).thenReturn(Optional.of(auditA));
        PollAudit auditB = PollAudit.builder().pollName(PREVIOUS_POLL_NAME_TEST).nextStartTime(startB).build();
        when(repository.findById(eq(PREVIOUS_POLL_NAME_TEST))).thenReturn(Optional.of(auditB));

        OffenderEvent oe = OffenderEvent.builder().build();
        when(externalApiService.getTestEvents(eq(startA), eq(endA), eq(false))).thenReturn(List.of(oe, oe, oe, oe));
        when(externalApiService.getTestEvents(eq(startB), eq(endB), eq(false))).thenReturn(List.of(oe, oe));

        eventRetrievalService.runTestPolls(now, false, 5);

        assertThat(auditA.getNumberOfRecords()).isEqualTo(4);
        assertThat(auditB.getNextStartTime()).isEqualTo(startA);
        assertThat(auditA.getNextStartTime()).isEqualTo(endA);
    }

    @Test
    public void testRunTestPollsFirstRun() {
        final var now = LocalDateTime.parse("2022-08-01T12:01:10");
        LocalDateTime startA = LocalDateTime.parse("2022-08-01T12:00:00");
        LocalDateTime endA = LocalDateTime.parse("2022-08-01T12:01:00");

        OffenderEvent oe = OffenderEvent.builder().build();
        when(externalApiService.getTestEvents(eq(startA), eq(endA), eq(true))).thenReturn(List.of(oe, oe, oe, oe));

        eventRetrievalService.runTestPolls(now, true, 10);

        verify(repository, times(1)).save(eq(
                PollAudit.builder().pollName(POLL_NAME_TEST).nextStartTime(endA).numberOfRecords(4).build()));
        verify(repository, times(1)).save(eq(
                PollAudit.builder().pollName(PREVIOUS_POLL_NAME_TEST).nextStartTime(startA).build()));
    }
}
