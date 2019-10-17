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
        eventRetrievalService = new EventRetrievalService(externalApiService, snsService, repository, 60000, maxEventRangeHours);
    }

    @Test
    public void testEventRetrievalNeverRunBefore() {

        final var lastRun = LocalDateTime.now().minusMinutes(1);

        when(repository.findById(eq(POLL_NAME))).thenReturn(Optional.empty());
        final var events = List.of
                (
                        OffenderEvent.builder().bookingId(-1L).eventDatetime(lastRun.plusSeconds(3)).build(),
                        OffenderEvent.builder().bookingId(-2L).eventDatetime(lastRun.plusSeconds(2)).build(),
                        OffenderEvent.builder().bookingId(-3L).eventDatetime(lastRun.plusSeconds(1)).build()
                );
        when(externalApiService.getEvents(any(), any())).thenReturn(events);

        snsService.sendEvent(eq(events.get(2)));
        snsService.sendEvent(eq(events.get(1)));
        snsService.sendEvent(eq(events.get(0)));

        final var resultPoll = PollAudit.builder().pollName(POLL_NAME).nextRunTime(events.get(2).getEventDatetime().plus(1, MICROS)).numberOfRecords(3).build();
        repository.save(eq(resultPoll));
        eventRetrievalService.pollEvents();

        verify(repository).findById(eq(POLL_NAME));

        verify(externalApiService).getEvents(any(), any());
        verify(snsService, times(3)).sendEvent(any(OffenderEvent.class));
    }


    @Test
    public void testEventRetrievalRanOneMinuteAgo() {

        final var lastRun = LocalDateTime.now().minusMinutes(1);

        when(repository.findById(eq(POLL_NAME)))
                .thenReturn(Optional.of(PollAudit.builder().pollName(POLL_NAME).nextRunTime(lastRun).build()));
        final var events = List.of
                (
                        OffenderEvent.builder().bookingId(-1L).eventDatetime(lastRun.plusSeconds(3)).build(),
                        OffenderEvent.builder().bookingId(-2L).eventDatetime(lastRun.plusSeconds(2)).build(),
                        OffenderEvent.builder().bookingId(-3L).eventDatetime(lastRun.plusSeconds(1)).build()
                );
        when(externalApiService.getEvents(eq(lastRun), any())).thenReturn(events);

        snsService.sendEvent(eq(events.get(2)));
        snsService.sendEvent(eq(events.get(1)));
        snsService.sendEvent(eq(events.get(0)));

        final var resultPoll = PollAudit.builder().pollName(POLL_NAME).nextRunTime(events.get(2).getEventDatetime().plus(1, MICROS)).numberOfRecords(3).build();
        repository.save(eq(resultPoll));
        eventRetrievalService.pollEvents();

        verify(repository).findById(eq(POLL_NAME));
        verify(repository, times(2)).save(any(PollAudit.class));
        verify(externalApiService).getEvents(eq(lastRun), any());
        verify(snsService, times(3)).sendEvent(any(OffenderEvent.class));
    }

    @Test
    public void testEventRetrievalRanOneDayAgo() {
        final var lastRun = LocalDateTime.now().minusDays(1);

        when(repository.findById(eq(POLL_NAME)))
                .thenReturn(Optional.of(PollAudit.builder().pollName(POLL_NAME).nextRunTime(lastRun).build()));
        final var events = List.of
                (
                        OffenderEvent.builder().bookingId(-1L).eventDatetime(lastRun.plusHours(4)).build(),
                        OffenderEvent.builder().bookingId(-2L).eventDatetime(lastRun.plusHours(3)).build(),
                        OffenderEvent.builder().bookingId(-3L).eventDatetime(lastRun.plusHours(2)).build(),
                        OffenderEvent.builder().bookingId(-4L).eventDatetime(lastRun.plusHours(1)).build()
                );
        when(externalApiService.getEvents(eq(lastRun), eq(lastRun.plusHours(maxEventRangeHours)))).thenReturn(events);

        snsService.sendEvent(eq(events.get(3)));
        snsService.sendEvent(eq(events.get(2)));
        snsService.sendEvent(eq(events.get(1)));
        snsService.sendEvent(eq(events.get(0)));

        final var resultPoll = PollAudit.builder().pollName(POLL_NAME).nextRunTime(events.get(2).getEventDatetime().plus(1, MICROS)).numberOfRecords(3).build();
        repository.save(eq(resultPoll));
        eventRetrievalService.pollEvents();

        verify(repository).findById(eq(POLL_NAME));
        verify(externalApiService).getEvents(eq(lastRun), eq(lastRun.plusHours(maxEventRangeHours)));
        verify(snsService, times(4)).sendEvent(any(OffenderEvent.class));
    }
}
