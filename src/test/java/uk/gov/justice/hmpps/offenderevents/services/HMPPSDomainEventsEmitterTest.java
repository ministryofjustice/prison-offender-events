package uk.gov.justice.hmpps.offenderevents.services;

import com.amazonaws.services.sns.AmazonSNSAsync;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.RecallReason;
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.Source;
import uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.ReleaseReason;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.stream.Stream;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.Reason.RECALL;
import static uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.Reason.UNKNOWN;
import static uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.Reason.TEMPORARY_ABSENCE_RELEASE;

@ExtendWith(MockitoExtension.class)
class HMPPSDomainEventsEmitterTest {
    private HMPPSDomainEventsEmitter emitter;

    @Mock
    private AmazonSNSAsync amazonSns;

    @Mock
    private ReceivePrisonerReasonCalculator receivePrisonerReasonCalculator;

    @Mock
    private ReleasePrisonerReasonCalculator releasePrisonerReasonCalculator;

    @Mock
    private TelemetryClient telemetryClient;

    @Captor
    private ArgumentCaptor<PublishRequest> publishRequestCaptor;

    @Captor
    private ArgumentCaptor<Map<String, String>> telemetryAttributesCaptor;

    @SuppressWarnings("unused")
    private static Stream<Arguments> eventMap() {
        return Stream.of(
            Arguments.of("OFFENDER_MOVEMENT-DISCHARGE", "prison-offender-events.prisoner.released"),
            Arguments.of("OFFENDER_MOVEMENT-RECEPTION", "prison-offender-events.prisoner.received")
        );
    }

    @BeforeEach
    void setUp() {
        emitter = new HMPPSDomainEventsEmitter(amazonSns,
            "sometopicarn",
            new ObjectMapper(),
            receivePrisonerReasonCalculator,
            releasePrisonerReasonCalculator,
            telemetryClient);
    }

    @Test
    @DisplayName("Will do nothing for insignificant events")
    void willDoNothingForInsignificantEvents() {
        emitter.convertAndSendWhenSignificant(OffenderEvent.builder().eventType("BALANCE_UPDATED").build());

        verifyNoInteractions(amazonSns);
    }

    @ParameterizedTest
    @MethodSource("eventMap")
    @DisplayName("Will send to topic for these events")
    @MockitoSettings(strictness = Strictness.LENIENT)
    void willSendToTopicForTheseEvents(String prisonEventType, String eventType) {
        when(receivePrisonerReasonCalculator.calculateMostLikelyReasonForPrisonerReceive(any()))
            .thenReturn(new RecallReason(UNKNOWN, Source.PRISON, CurrentLocation.IN_PRISON, CurrentPrisonStatus.UNDER_PRISON_CARE));
        when(releasePrisonerReasonCalculator.calculateReasonForRelease(any()))
            .thenReturn(new ReleaseReason(TEMPORARY_ABSENCE_RELEASE, CurrentLocation.OUTSIDE_PRISON, CurrentPrisonStatus.NOT_UNDER_PRISON_CARE));


        emitter.convertAndSendWhenSignificant(OffenderEvent
            .builder()
            .eventType(prisonEventType)
            .offenderIdDisplay("A1234GH")
            .eventDatetime(LocalDateTime.now())
            .build());

        verify(amazonSns).publish(publishRequestCaptor.capture());

        final var payload = publishRequestCaptor.getValue().getMessage();
        final var messageAttributes = publishRequestCaptor.getValue().getMessageAttributes();

        assertThatJson(payload).node("eventType").isEqualTo(eventType);
        assertThatJson(payload).node("version").isEqualTo(1);
        assertThat(messageAttributes.get("eventType"))
            .isEqualTo(new MessageAttributeValue().withStringValue(eventType).withDataType("String"));

        verify(telemetryClient).trackEvent(eq(eventType), anyMap(), isNull());
    }

    @Nested
    class PrisonerReceived {
        private String payload;
        private Map<String, String> telemetryAttributes;

        @BeforeEach
        void setUp() {
            when(receivePrisonerReasonCalculator.calculateMostLikelyReasonForPrisonerReceive(any()))
                .thenReturn(new RecallReason(RECALL, Source.PRISON, "some details", CurrentLocation.IN_PRISON, CurrentPrisonStatus.UNDER_PRISON_CARE));

            emitter.convertAndSendWhenSignificant(OffenderEvent
                .builder()
                .eventType("OFFENDER_MOVEMENT-RECEPTION")
                .offenderIdDisplay("A1234GH")
                .eventDatetime(LocalDateTime.parse("2020-12-04T10:42:43"))
                .build());
            verify(amazonSns).publish(publishRequestCaptor.capture());
            payload = publishRequestCaptor.getValue().getMessage();
            verify(telemetryClient).trackEvent(any(), telemetryAttributesCaptor.capture(), isNull());
            telemetryAttributes = telemetryAttributesCaptor.getValue();
        }

        @Test
        @DisplayName("will use event datetime for occurred at time")
        void willUseEventDatetimeForOccurredAtTime() {
            assertThatJson(payload).node("occurredAt").isEqualTo("2020-12-04T10:42:43");
        }

        @Test
        @DisplayName("will user current time as publishedAt")
        void willUserCurrentTimeAsPublishedAt() {
            assertThatJson(payload)
                .node("publishedAt")
                .asString()
                .satisfies(publishedAt -> assertThat(LocalDateTime.parse(publishedAt))
                    .isCloseTo(LocalDateTime.now(), within(10, ChronoUnit.SECONDS)));

        }

        @Test
        @DisplayName("additionalInformation will contain offenderNumber as NOMS number")
        void additionalInformationWillContainOffenderNumberAsNOMSNumber() {
            assertThatJson(payload).node("additionalInformation.nomsNumber").isEqualTo("A1234GH");
        }

        @Test
        @DisplayName("will describe the prisoners last (or current) location")
        void additionalInformationWillContainPrisonId() {
            assertThatJson(payload).node("additionalInformation.prisonId").isEqualTo("MDI");
        }

        @Test
        @DisplayName("will indicate the reason for a prisoners entry")
        void willIndicateTheReasonForAPrisonersEntry() {
            assertThatJson(payload).node("additionalInformation.reason").isEqualTo("RECALL");
        }

        @Test
        @DisplayName("will describe the event as a receive")
        void willDescribeTheEventAsAReceive() {
            assertThatJson(payload).node("description").isEqualTo("A prisoner has been received into prison");
        }

        @Test
        @DisplayName("will describe the prisoners current location and status")
        void willDescribeThePrisonersCurrentLocation() {
            assertThatJson(payload).node("additionalInformation.currentLocation").isEqualTo("IN_PRISON");
            assertThatJson(payload).node("additionalInformation.currentPrisonStatus").isEqualTo("UNDER_PRISON_CARE");
        }

        @Test
        @DisplayName("will add noms number to telemetry event")
        void willAddNomsNumberToTelemetryEvent() {
            assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH");
        }

        @Test
        @DisplayName("will add reason to telemetry event")
        void willAddReasonToTelemetryEvent() {
            assertThat(telemetryAttributes).containsEntry("reason", "RECALL");
        }

        @Test
        @DisplayName("will add occurredAt to telemetry event")
        void willAddOccurredAtToTelemetryEvent() {
            assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-12-04T10:42:43");
        }

        @Test
        @DisplayName("will add source to telemetry event")
        void willAddSourceToTelemetryEvent() {
            assertThat(telemetryAttributes).containsEntry("source", "PRISON");
        }

        @Test
        @DisplayName("will add details to telemetry event when present")
        void willAddDetailsToTelemetryEvent() {
            assertThat(telemetryAttributes).containsEntry("details", "some details");
        }

        @Test
        @DisplayName("will add the prisoners current location and status to telemetry")
        void wilAddThePrisonersCurrentLocationAndStatusToTelemetry() {
            assertThat(telemetryAttributes).containsEntry("currentLocation", "IN_PRISON");
            assertThat(telemetryAttributes).containsEntry("currentPrisonStatus", "UNDER_PRISON_CARE");
        }

    }

    @Nested
    class PrisonerReleased {
        private String payload;
        private Map<String, String> telemetryAttributes;

        @BeforeEach
        void setUp() {
            when(releasePrisonerReasonCalculator.calculateReasonForRelease(any()))
                .thenReturn(new ReleaseReason(TEMPORARY_ABSENCE_RELEASE, "some details", CurrentLocation.OUTSIDE_PRISON, CurrentPrisonStatus.NOT_UNDER_PRISON_CARE));

            emitter.convertAndSendWhenSignificant(OffenderEvent
                .builder()
                .eventType("OFFENDER_MOVEMENT-DISCHARGE")
                .offenderIdDisplay("A1234GH")
                .eventDatetime(LocalDateTime.parse("2020-12-04T10:42:43"))
                .build());
            verify(amazonSns).publish(publishRequestCaptor.capture());
            payload = publishRequestCaptor.getValue().getMessage();
            verify(telemetryClient).trackEvent(any(), telemetryAttributesCaptor.capture(), isNull());
            telemetryAttributes = telemetryAttributesCaptor.getValue();
        }

        @Test
        @DisplayName("will use event datetime for occurred at time")
        void willUseEventDatetimeForOccurredAtTime() {
            assertThatJson(payload).node("occurredAt").isEqualTo("2020-12-04T10:42:43");
        }

        @Test
        @DisplayName("will user current time as publishedAt")
        void willUserCurrentTimeAsPublishedAt() {
            assertThatJson(payload)
                .node("publishedAt")
                .asString()
                .satisfies(publishedAt -> assertThat(LocalDateTime.parse(publishedAt))
                    .isCloseTo(LocalDateTime.now(), within(10, ChronoUnit.SECONDS)));

        }

        @Test
        @DisplayName("additionalInformation will contain offenderNumber as NOMS number")
        void additionalInformationWillContainOffenderNumberAsNOMSNumber() {
            assertThatJson(payload).node("additionalInformation.nomsNumber").isEqualTo("A1234GH");
        }

        @Test
        @DisplayName("will describe the prisoners last (or current) location")
        void additionalInformationWillContainPrisonId() {
            assertThatJson(payload).node("additionalInformation.prisonId").isEqualTo("MDI");
        }

        @Test
        @DisplayName("will indicate the reason for a prisoners exit")
        void willIndicateTheReasonForAPrisonersExit() {
            assertThatJson(payload).node("additionalInformation.reason").isEqualTo("TEMPORARY_ABSENCE_RELEASE");
        }


        @Test
        @DisplayName("will describe the event as a release")
        void willDescribeTheEventAsAReceive() {
            assertThatJson(payload).node("description").isEqualTo("A prisoner has been released from prison");
        }

        @Test
        @DisplayName("will describe the prisoners current location and status")
        void willDescribeThePrisonersCurrentLocation() {
            assertThatJson(payload).node("additionalInformation.currentLocation").isEqualTo("OUTSIDE_PRISON");
            assertThatJson(payload)
                .node("additionalInformation.currentPrisonStatus")
                .isEqualTo("NOT_UNDER_PRISON_CARE");
        }

        @Test
        @DisplayName("will add noms number to telemetry event")
        void willAddNomsNumberToTelemetryEvent() {
            assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH");
        }

        @Test
        @DisplayName("will add reason to telemetry event")
        void willAddReasonToTelemetryEvent() {
            assertThat(telemetryAttributes).containsEntry("reason", "TEMPORARY_ABSENCE_RELEASE");
        }

        @Test
        @DisplayName("will add occurredAt to telemetry event")
        void willAddOccurredAtToTelemetryEvent() {
            assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-12-04T10:42:43");
        }

        @Test
        @DisplayName("will add details to telemetry event when present")
        void willAddDetailsToTelemetryEvent() {
            assertThat(telemetryAttributes).containsEntry("details", "some details");
        }

        @Test
        @DisplayName("source will be absent from event and telemetry when not present")
        void sourceWillBeAbsentFromEventAndTelemetryWhenNotPresent() {
            assertThatJson(payload).node("additionalInformation.source").isAbsent();
            assertThat(telemetryAttributes).doesNotContainKey("source");
        }

        @Test
        @DisplayName("will add the prisoners current location and status to telemetry")
        void wilAddThePrisonersCurrentLocationAndStatusToTelemetry() {
            assertThat(telemetryAttributes).containsEntry("currentLocation", "OUTSIDE_PRISON");
            assertThat(telemetryAttributes).containsEntry("currentPrisonStatus", "NOT_UNDER_PRISON_CARE");
        }

    }
}
