package uk.gov.justice.hmpps.offenderevents.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.gov.justice.hmpps.offenderevents.services.EventRetrievalService.POLL_NAME;

@ExtendWith(MockitoExtension.class)
public class EventRetrievalServiceTest {

    public static final int WIND_BACK_SECONDS = 400;
    public static final int POLL_INTERVAL = 60000;

    @Mock
    private ExternalApiService externalApiService;
    @Mock
    private PrisonEventsEmitter prisonEventsEmitter;
    @Mock
    private HMPPSDomainEventsEmitter hmppsDomainEventsEmitter;

    @Mock
    private PollAuditRepository repository;

    private int maxEventRangeMinutes = 60;

    private EventRetrievalService eventRetrievalService;

    @BeforeEach
    public void setup() {
        eventRetrievalService = new EventRetrievalService(externalApiService, prisonEventsEmitter, hmppsDomainEventsEmitter, repository, POLL_INTERVAL, WIND_BACK_SECONDS, maxEventRangeMinutes);
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

        final var resultPoll = PollAudit.builder().pollName(POLL_NAME).nextStartTime(events.get(1).getEventDatetime().plus(1, MICROS)).numberOfRecords(2).build();

        eventRetrievalService.pollEvents(now);

        verify(repository, times(1)).findById(eq(POLL_NAME));
        verify(externalApiService).getEvents(eq(lastRun), eq(windBackPoint));
        verify(prisonEventsEmitter, times(2)).sendEvent(any(OffenderEvent.class));
    }

    @Test
    @DisplayName("Will pass event to HMPPS domain emitter as well an main topic")
    void willPassEventToHMPPSDomainEmitter() {
        final var now = LocalDateTime.now();

        when(repository.findById(eq(POLL_NAME)))
            .thenReturn(Optional.of(PollAudit.builder().pollName(POLL_NAME).nextStartTime(LocalDateTime.now().minusMinutes(10)).build()));
        final var events = List.of
            (
                OffenderEvent.builder().eventType("OFFENDER_MOVEMENT-RECEPTION").bookingId(-1L).eventDatetime(now).build(),
                OffenderEvent.builder().eventType("OFFENDER_ATE_A_BANANA").bookingId(-2L).eventDatetime(now).build()
            );
        when(externalApiService.getEvents(any(), any())).thenReturn(events);

        eventRetrievalService.pollEvents(now);

        verify(prisonEventsEmitter, times(2)).sendEvent(any(OffenderEvent.class));
        verify(hmppsDomainEventsEmitter, times(2)).convertAndSendWhenSignificant(any(OffenderEvent.class));
    }

}
