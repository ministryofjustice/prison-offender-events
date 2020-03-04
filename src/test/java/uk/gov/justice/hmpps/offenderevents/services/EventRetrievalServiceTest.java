package uk.gov.justice.hmpps.offenderevents.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;
import uk.gov.justice.hmpps.offenderevents.model.PollAudit;
import uk.gov.justice.hmpps.offenderevents.repository.PollAuditRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.MICROS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.gov.justice.hmpps.offenderevents.services.EventRetrievalService.POLL_NAME;

@RunWith(MockitoJUnitRunner.class)
public class EventRetrievalServiceTest {

    public static final int WIND_BACK_SECONDS = 400;
    public static final int POLL_INTERVAL = 60000;
    @Mock
    private ExternalApiService externalApiService;
    @Mock
    private SnsService snsService;
    @Mock
    private PollAuditRepository repository;

    private int maxEventRangeHours = 1;

    private EventRetrievalService eventRetrievalService;

    @Before
    public void setup() {
        eventRetrievalService = new EventRetrievalService(externalApiService, snsService, repository, POLL_INTERVAL, WIND_BACK_SECONDS, maxEventRangeHours);
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

        snsService.sendEvent(eq(events.get(2)));
        snsService.sendEvent(eq(events.get(1)));
        snsService.sendEvent(eq(events.get(0)));

        final var resultPoll = PollAudit.builder().pollName(POLL_NAME).nextRunTime(events.get(2).getEventDatetime().plus(1, MICROS)).numberOfRecords(3).build();
        repository.save(eq(resultPoll));
        eventRetrievalService.pollEvents(now);

        verify(repository).findById(eq(POLL_NAME));

        verify(externalApiService).getEvents(eq(eventTimeStart), eq(windBackPoint));
        verify(snsService, times(3)).sendEvent(any(OffenderEvent.class));
    }


    @Test
    public void testEventRetrievalWhereLastRunWasWindBackWindowsAndOneMinute() {

        final var now = LocalDateTime.now();
        final var windBackPoint = now.minusSeconds(WIND_BACK_SECONDS);
        final var lastRun = windBackPoint.minusMinutes(1);

        when(repository.findById(eq(POLL_NAME)))
                .thenReturn(Optional.of(PollAudit.builder().pollName(POLL_NAME).nextRunTime(lastRun).build()));
        final var events = List.of
                (
                        OffenderEvent.builder().bookingId(-1L).eventDatetime(lastRun.plusSeconds(3)).build(),
                        OffenderEvent.builder().bookingId(-2L).eventDatetime(lastRun.plusSeconds(2)).build(),
                        OffenderEvent.builder().bookingId(-3L).eventDatetime(lastRun.plusSeconds(1)).build()
                );
        when(externalApiService.getEvents(eq(lastRun), eq(windBackPoint))).thenReturn(events);

        snsService.sendEvent(eq(events.get(2)));
        snsService.sendEvent(eq(events.get(1)));
        snsService.sendEvent(eq(events.get(0)));

        final var resultPoll = PollAudit.builder().pollName(POLL_NAME).nextRunTime(events.get(2).getEventDatetime().plus(1, MICROS)).numberOfRecords(3).build();
        repository.save(eq(resultPoll));
        eventRetrievalService.pollEvents(now);

        verify(repository).findById(eq(POLL_NAME));
        verify(repository, times(2)).save(any(PollAudit.class));
        verify(externalApiService).getEvents(eq(lastRun), eq(windBackPoint));
        verify(snsService, times(3)).sendEvent(any(OffenderEvent.class));
    }

    @Test
    public void testEventRetrievalRanOneDayAgo() {
        final var now = LocalDateTime.now();
        final var lastRun = now.minusDays(1);

        when(repository.findById(eq(POLL_NAME)))
                .thenReturn(Optional.of(PollAudit.builder().pollName(POLL_NAME).nextRunTime(lastRun).build()));
        final var events = List.of
                (
                        OffenderEvent.builder().bookingId(-1L).eventDatetime(lastRun.plusMinutes(15)).build(),
                        OffenderEvent.builder().bookingId(-2L).eventDatetime(lastRun.plusMinutes(30)).build(),
                        OffenderEvent.builder().bookingId(-3L).eventDatetime(lastRun.plusMinutes(45)).build(),
                        OffenderEvent.builder().bookingId(-4L).eventDatetime(lastRun.plusMinutes(60)).build()
                );
        when(externalApiService.getEvents(eq(lastRun), eq(lastRun.plusHours(maxEventRangeHours)))).thenReturn(events);

        snsService.sendEvent(eq(events.get(3)));
        snsService.sendEvent(eq(events.get(2)));
        snsService.sendEvent(eq(events.get(1)));
        snsService.sendEvent(eq(events.get(0)));

        final var resultPoll = PollAudit.builder().pollName(POLL_NAME).nextRunTime(events.get(2).getEventDatetime().plus(1, MICROS)).numberOfRecords(3).build();
        repository.save(eq(resultPoll));
        eventRetrievalService.pollEvents(now);

        verify(repository).findById(eq(POLL_NAME));
        verify(externalApiService).getEvents(eq(lastRun), eq(lastRun.plusHours(maxEventRangeHours)));
        verify(snsService, times(4)).sendEvent(any(OffenderEvent.class));
    }

    @Test
    public void testEventRetrievalRanAMinuteAgoBeforeNewWindBackWindowAdded() {
        final var now = LocalDateTime.now();
        final var lastRun = now.minusMinutes(1);

        when(repository.findById(eq(POLL_NAME)))
                .thenReturn(Optional.of(PollAudit.builder().pollName(POLL_NAME).nextRunTime(lastRun).build()));

        eventRetrievalService.pollEvents(now);

        verify(repository, times(1)).findById(eq(POLL_NAME));
    }

}
