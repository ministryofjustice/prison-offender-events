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
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.Reason;
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.RecallReason;
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.Source;

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

@ExtendWith(MockitoExtension.class)
class HMPPSDomainEventsEmitterTest {
    private HMPPSDomainEventsEmitter emitter;

    @Mock
    private AmazonSNSAsync amazonSns;

    @Mock
    private ReceivePrisonerReasonCalculator receivePrisonerReasonCalculator;

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
        emitter = new HMPPSDomainEventsEmitter(amazonSns, "sometopicarn", new ObjectMapper(), receivePrisonerReasonCalculator, telemetryClient);
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
        when(receivePrisonerReasonCalculator.calculateMostLikelyReasonForPrisoner(any())).thenReturn(new RecallReason(Reason.UNKNOWN, Source.PRISON));

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
            when(receivePrisonerReasonCalculator.calculateMostLikelyReasonForPrisoner(any())).thenReturn(new RecallReason(Reason.RECALL, Source.PRISON));

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
    }

    @Nested
    class PrisonerReleased {
        private String payload;
        private Map<String, String> telemetryAttributes;

        @BeforeEach
        void setUp() {
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
        @DisplayName("will user current time as publishedAT")
        void willUserCurrentTimeAsPublishedAT() {
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
        @DisplayName("will describe the event as a release")
        void willDescribeTheEventAsAReceive() {
            assertThatJson(payload).node("description").isEqualTo("A prisoner has been released from prison");
        }
        @Test
        @DisplayName("will add noms number to telemetry event")
        void willAddNomsNumberToTelemetryEvent() {
            assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH");
        }

        @Test
        @DisplayName("will add reason to telemetry event")
        void willAddReasonToTelemetryEvent() {
            assertThat(telemetryAttributes).containsEntry("reason", "UNKNOWN");
        }

        @Test
        @DisplayName("will add occurredAt to telemetry event")
        void willAddOccurredAtToTelemetryEvent() {
            assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-12-04T10:42:43");
        }
    }
}
