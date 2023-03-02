package uk.gov.justice.hmpps.offenderevents.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness.LENIENT
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.hmpps.offenderevents.config.OffenderEventsProperties
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent
import uk.gov.justice.hmpps.offenderevents.services.CurrentLocation.IN_PRISON
import uk.gov.justice.hmpps.offenderevents.services.CurrentLocation.OUTSIDE_PRISON
import uk.gov.justice.hmpps.offenderevents.services.CurrentPrisonStatus.NOT_UNDER_PRISON_CARE
import uk.gov.justice.hmpps.offenderevents.services.CurrentPrisonStatus.UNDER_PRISON_CARE
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.ProbableCause
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.ReceiveReason
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.Source.PRISON
import uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.MovementReason
import uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.Reason
import uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.Reason.TEMPORARY_ABSENCE_RELEASE
import uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.ReleaseReason
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit.SECONDS
import java.util.function.Consumer

@TestInstance(PER_CLASS)
@ExtendWith(MockitoExtension::class)
internal class HMPPSDomainEventsEmitterTest {
  private lateinit var emitter: HMPPSDomainEventsEmitter

  @Mock
  private lateinit var receivePrisonerReasonCalculator: ReceivePrisonerReasonCalculator

  @Mock
  private lateinit var releasePrisonerReasonCalculator: ReleasePrisonerReasonCalculator

  @Mock
  private lateinit var mergeRecordDiscriminator: MergeRecordDiscriminator

  @Mock
  private lateinit var telemetryClient: TelemetryClient

  @Mock
  private lateinit var offenderEventsProperties: OffenderEventsProperties

  @Captor
  private lateinit var publishRequestCaptor: ArgumentCaptor<PublishRequest>

  @Captor
  private lateinit var telemetryAttributesCaptor: ArgumentCaptor<Map<String, String>>

  private val hmppsQueueService = mock<HmppsQueueService>()
  private var hmppsEventSnsClient = mock<SnsAsyncClient>()

  @BeforeEach
  fun setUp() {
    hmppsEventSnsClient = mock()
    whenever(hmppsQueueService.findByTopicId("hmppseventtopic"))
      .thenReturn(HmppsTopic("hmppseventtopic", "sometopicarn", hmppsEventSnsClient))

    emitter = HMPPSDomainEventsEmitter(
      hmppsQueueService,
      ObjectMapper(),
      receivePrisonerReasonCalculator,
      releasePrisonerReasonCalculator,
      mergeRecordDiscriminator,
      telemetryClient,
      offenderEventsProperties,
    )
  }

  @Test
  @DisplayName("Will do nothing for insignificant events")
  fun willDoNothingForInsignificantEvents() {
    emitter.convertAndSendWhenSignificant(OffenderEvent.builder().eventType("BALANCE_UPDATED").build())
    Mockito.verifyNoInteractions(hmppsEventSnsClient)
  }

  @ParameterizedTest
  @MethodSource("eventMap")
  @DisplayName("Will send to topic for these events")
  @MockitoSettings(strictness = LENIENT)
  fun willSendToTopicForTheseEvents(prisonEventType: String, eventType: String) {
    whenever(receivePrisonerReasonCalculator.calculateMostLikelyReasonForPrisonerReceive(ArgumentMatchers.any()))
      .thenReturn(
        ReceiveReason(
          ReceivePrisonerReasonCalculator.Reason.ADMISSION,
          ProbableCause.UNKNOWN,
          PRISON,
          IN_PRISON,
          UNDER_PRISON_CARE,
          "MDI",
          ReceivePrisonerReasonCalculator.MovementReason("N"),
        ),
      )
    whenever(releasePrisonerReasonCalculator.calculateReasonForRelease(ArgumentMatchers.any()))
      .thenReturn(
        ReleaseReason(
          TEMPORARY_ABSENCE_RELEASE,
          OUTSIDE_PRISON,
          NOT_UNDER_PRISON_CARE,
          "MDI",
          MovementReason("N"),
        ),
      )
    emitter.convertAndSendWhenSignificant(
      OffenderEvent.builder()
        .eventType(prisonEventType)
        .offenderIdDisplay("A1234GH")
        .eventDatetime(LocalDateTime.now())
        .build(),
    )

    verify(hmppsEventSnsClient, times(1)).publish(publishRequestCaptor.capture())
    val payload = publishRequestCaptor.value.message()
    val messageAttributes = publishRequestCaptor.value.messageAttributes()
    assertThatJson(payload).node("eventType").isEqualTo(eventType)
    assertThatJson(payload).node("version").isEqualTo(1)
    assertThat(
      messageAttributes["eventType"],
    )
      .isEqualTo(MessageAttributeValue.builder().stringValue(eventType).dataType("String").build())
    verify(telemetryClient)!!
      .trackEvent(ArgumentMatchers.eq(eventType), ArgumentMatchers.anyMap(), ArgumentMatchers.isNull())
  }

  @ParameterizedTest
  @MethodSource("bookingChangedEventMap")
  @DisplayName("Will send to topic for these booking changed events")
  @MockitoSettings(strictness = LENIENT)
  fun willSendToTopicForBookingChangedEvent(prisonEventType: String, eventType: String) {
    whenever(mergeRecordDiscriminator.identifyMergedPrisoner(ArgumentMatchers.eq(43124234L)))
      .thenReturn(
        listOf(
          MergeRecordDiscriminator.MergeOutcome("A1234GH", "A1233GP"),
        ),
      )

    emitter.convertAndSendWhenSignificant(
      OffenderEvent.builder()
        .eventType(prisonEventType)
        .offenderIdDisplay("A1234GH")
        .bookingId(43124234L)
        .eventDatetime(LocalDateTime.now())
        .build(),
    )

    verify(hmppsEventSnsClient, times(1)).publish(publishRequestCaptor.capture())
    val payload = publishRequestCaptor.value.message()
    val messageAttributes = publishRequestCaptor.value.messageAttributes()
    assertThatJson(payload).node("eventType").isEqualTo(eventType)
    assertThatJson(payload).node("version").isEqualTo(1)
    assertThat(
      messageAttributes["eventType"],
    )
      .isEqualTo(MessageAttributeValue.builder().stringValue(eventType).dataType("String").build())
    verify(telemetryClient)!!
      .trackEvent(ArgumentMatchers.eq(eventType), ArgumentMatchers.anyMap(), ArgumentMatchers.isNull())
  }

  @Nested
  internal inner class PrisonerReceived {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      whenever(
        receivePrisonerReasonCalculator.calculateMostLikelyReasonForPrisonerReceive(
          ArgumentMatchers.any(),
        ),
      )
        .thenReturn(
          ReceiveReason(
            ReceivePrisonerReasonCalculator.Reason.ADMISSION,
            ProbableCause.RECALL,
            PRISON,
            "some details",
            IN_PRISON,
            UNDER_PRISON_CARE,
            "MDI",
            ReceivePrisonerReasonCalculator.MovementReason("N"),
          ),
        )
      emitter.convertAndSendWhenSignificant(
        OffenderEvent.builder()
          .eventType("OFFENDER_MOVEMENT-RECEPTION")
          .offenderIdDisplay("A1234GH")
          .eventDatetime(LocalDateTime.parse("2020-12-04T10:42:43"))
          .build(),
      )

      verify(hmppsEventSnsClient, times(1)).publish(publishRequestCaptor.capture())
      payload = publishRequestCaptor.value.message()
      verify(telemetryClient)!!
        .trackEvent(ArgumentMatchers.any(), telemetryAttributesCaptor.capture(), ArgumentMatchers.isNull())
      telemetryAttributes = telemetryAttributesCaptor.value
    }

    @Test
    @DisplayName("will use event datetime for occurred at time")
    fun willUseEventDatetimeForOccurredAtTime() {
      assertThatJson(payload).node("occurredAt").isEqualTo("2020-12-04T10:42:43Z")
    }

    @Test
    @DisplayName("will user current time as publishedAt")
    fun willUserCurrentTimeAsPublishedAt() {
      assertThatJson(payload)
        .node("publishedAt")
        .asString()
        .satisfies(
          Consumer { publishedAt: String? ->
            assertThat(OffsetDateTime.parse(publishedAt))
              .isCloseTo(OffsetDateTime.now(), Assertions.within(10, SECONDS))
          },
        )
    }

    @Test
    @DisplayName("additionalInformation will contain offenderNumber as NOMS number")
    fun additionalInformationWillContainOffenderNumberAsNOMSNumber() {
      assertThatJson(payload).node("additionalInformation.nomsNumber").isEqualTo("A1234GH")
    }

    @Test
    @DisplayName("will describe the prisoners last (or current) location")
    fun additionalInformationWillContainPrisonId() {
      assertThatJson(payload).node("additionalInformation.prisonId").isEqualTo("MDI")
    }

    @Test
    @DisplayName("will indicate the reason for a prisoners entry")
    fun willIndicateTheReasonForAPrisonersEntry() {
      assertThatJson(payload).node("additionalInformation.reason").isEqualTo("ADMISSION")
    }

    @Test
    @DisplayName("will indicate the probable cause for a prisoners entry")
    fun willIndicateTheProbableCauseForAPrisonersEntry() {
      assertThatJson(payload).node("additionalInformation.probableCause").isEqualTo("RECALL")
    }

    @Test
    @DisplayName("will describe the event as a receive")
    fun willDescribeTheEventAsAReceive() {
      assertThatJson(payload).node("description")
        .isEqualTo("A prisoner has been received into prison")
    }

    @Test
    @DisplayName("will pass through the nomis movement reason code")
    fun willPassThroughNOMISReasonCode() {
      assertThatJson(payload).node("additionalInformation.nomisMovementReasonCode")
        .isEqualTo("N")
    }

    @Test
    @DisplayName("will describe the prisoners current location and status")
    fun willDescribeThePrisonersCurrentLocation() {
      assertThatJson(payload).node("additionalInformation.currentLocation").isEqualTo("IN_PRISON")
      assertThatJson(payload).node("additionalInformation.currentPrisonStatus")
        .isEqualTo("UNDER_PRISON_CARE")
    }

    @Test
    @DisplayName("will add noms number to telemetry event")
    fun willAddNomsNumberToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
    }

    @Test
    @DisplayName("will add reason to telemetry event")
    fun willAddReasonToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("reason", "ADMISSION")
    }

    @Test
    @DisplayName("will add probable cause to telemetry event")
    fun willAddProbableCauseToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("probableCause", "RECALL")
    }

    @Test
    @DisplayName("will add occurredAt to telemetry event")
    fun willAddOccurredAtToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-12-04T10:42:43Z")
    }

    @Test
    @DisplayName("will add source to telemetry event")
    fun willAddSourceToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("source", "PRISON")
    }

    @Test
    @DisplayName("will add details to telemetry event when present")
    fun willAddDetailsToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("details", "some details")
    }

    @Test
    @DisplayName("will add the prisoners current location and status to telemetry")
    fun wilAddThePrisonersCurrentLocationAndStatusToTelemetry() {
      assertThat(telemetryAttributes).containsEntry("currentLocation", "IN_PRISON")
      assertThat(telemetryAttributes).containsEntry("currentPrisonStatus", "UNDER_PRISON_CARE")
    }
  }

  @Nested
  internal inner class PrisonerNotReallyReceived {
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      whenever(receivePrisonerReasonCalculator.calculateMostLikelyReasonForPrisonerReceive(ArgumentMatchers.any()))
        .thenReturn(
          ReceiveReason(
            ReceivePrisonerReasonCalculator.Reason.ADMISSION,
            ProbableCause.UNKNOWN,
            PRISON,
            "some details",
            OUTSIDE_PRISON,
            NOT_UNDER_PRISON_CARE,
            "MDI",
            ReceivePrisonerReasonCalculator.MovementReason("N"),
          ),
        )
      emitter.convertAndSendWhenSignificant(
        OffenderEvent.builder()
          .eventType("OFFENDER_MOVEMENT-RECEPTION")
          .offenderIdDisplay("A1234GH")
          .eventDatetime(LocalDateTime.parse("2020-12-04T10:42:43"))
          .build(),
      )
      Mockito.verifyNoInteractions(hmppsEventSnsClient)
      verify(telemetryClient)!!
        .trackEvent(ArgumentMatchers.any(), telemetryAttributesCaptor.capture(), ArgumentMatchers.isNull())
      telemetryAttributes = telemetryAttributesCaptor.value
    }

    @Test
    @DisplayName("will add noms number to telemetry event")
    fun willAddNomsNumberToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
    }

    @Test
    @DisplayName("will add reason to telemetry event")
    fun willAddReasonToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("reason", "ADMISSION")
    }

    @Test
    @DisplayName("will add occurredAt to telemetry event")
    fun willAddOccurredAtToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-12-04T10:42:43")
    }

    @Test
    @DisplayName("will add details to telemetry event when present")
    fun willAddDetailsToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("details", "some details")
    }

    @Test
    @DisplayName("will add the prisoners current location and status to telemetry")
    fun wilAddThePrisonersCurrentLocationAndStatusToTelemetry() {
      assertThat(telemetryAttributes).containsEntry("currentLocation", "OUTSIDE_PRISON")
      assertThat(telemetryAttributes).containsEntry("currentPrisonStatus", "NOT_UNDER_PRISON_CARE")
    }
  }

  @Nested
  internal inner class PrisonerReleased {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      whenever(releasePrisonerReasonCalculator.calculateReasonForRelease(ArgumentMatchers.any()))
        .thenReturn(
          ReleaseReason(
            TEMPORARY_ABSENCE_RELEASE,
            "some details",
            OUTSIDE_PRISON,
            NOT_UNDER_PRISON_CARE,
            "MDI",
            MovementReason("N"),
          ),
        )
      emitter.convertAndSendWhenSignificant(
        OffenderEvent.builder()
          .eventType("OFFENDER_MOVEMENT-DISCHARGE")
          .offenderIdDisplay("A1234GH")
          .eventDatetime(LocalDateTime.parse("2020-07-04T10:42:43"))
          .build(),
      )
      verify(hmppsEventSnsClient, times(1)).publish(publishRequestCaptor.capture())
      payload = publishRequestCaptor.value.message()
      verify(telemetryClient)!!
        .trackEvent(ArgumentMatchers.any(), telemetryAttributesCaptor.capture(), ArgumentMatchers.isNull())
      telemetryAttributes = telemetryAttributesCaptor.value
    }

    @Test
    @DisplayName("will use event datetime for occurred at time")
    fun willUseEventDatetimeForOccurredAtTime() {
      assertThatJson(payload).node("occurredAt").isEqualTo("2020-07-04T10:42:43+01:00")
    }

    @Test
    @DisplayName("will user current time as publishedAt")
    fun willUserCurrentTimeAsPublishedAt() {
      assertThatJson(payload)
        .node("publishedAt")
        .asString()
        .satisfies(
          Consumer { publishedAt: String? ->
            assertThat(OffsetDateTime.parse(publishedAt))
              .isCloseTo(OffsetDateTime.now(), Assertions.within(10, SECONDS))
          },
        )
    }

    @Test
    @DisplayName("additionalInformation will contain offenderNumber as NOMS number")
    fun additionalInformationWillContainOffenderNumberAsNOMSNumber() {
      assertThatJson(payload).node("additionalInformation.nomsNumber").isEqualTo("A1234GH")
    }

    @Test
    @DisplayName("will describe the prisoners last (or current) location")
    fun additionalInformationWillContainPrisonId() {
      assertThatJson(payload).node("additionalInformation.prisonId").isEqualTo("MDI")
    }

    @Test
    @DisplayName("will indicate the reason for a prisoners exit")
    fun willIndicateTheReasonForAPrisonersExit() {
      assertThatJson(payload).node("additionalInformation.reason")
        .isEqualTo("TEMPORARY_ABSENCE_RELEASE")
    }

    @Test
    @DisplayName("will pass through the nomis movement reason code")
    fun willPassThroughNOMISReasonCode() {
      assertThatJson(payload).node("additionalInformation.nomisMovementReasonCode")
        .isEqualTo("N")
    }

    @Test
    @DisplayName("will describe the event as a release")
    fun willDescribeTheEventAsAReceive() {
      assertThatJson(payload).node("description")
        .isEqualTo("A prisoner has been released from prison")
    }

    @Test
    @DisplayName("will describe the prisoners current location and status")
    fun willDescribeThePrisonersCurrentLocation() {
      assertThatJson(payload).node("additionalInformation.currentLocation")
        .isEqualTo("OUTSIDE_PRISON")
      assertThatJson(payload)
        .node("additionalInformation.currentPrisonStatus")
        .isEqualTo("NOT_UNDER_PRISON_CARE")
    }

    @Test
    @DisplayName("will add noms number to telemetry event")
    fun willAddNomsNumberToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
    }

    @Test
    @DisplayName("will add reason to telemetry event")
    fun willAddReasonToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("reason", "TEMPORARY_ABSENCE_RELEASE")
    }

    @Test
    @DisplayName("will add occurredAt to telemetry event")
    fun willAddOccurredAtToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-07-04T10:42:43+01:00")
    }

    @Test
    @DisplayName("will add details to telemetry event when present")
    fun willAddDetailsToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("details", "some details")
    }

    @Test
    @DisplayName("source will be absent from event and telemetry when not present")
    fun sourceWillBeAbsentFromEventAndTelemetryWhenNotPresent() {
      assertThatJson(payload).node("additionalInformation.source").isAbsent()
      assertThat(telemetryAttributes).doesNotContainKey("source")
    }

    @Test
    @DisplayName("will add the prisoners current location and status to telemetry")
    fun wilAddThePrisonersCurrentLocationAndStatusToTelemetry() {
      assertThat(telemetryAttributes).containsEntry("currentLocation", "OUTSIDE_PRISON")
      assertThat(telemetryAttributes).containsEntry("currentPrisonStatus", "NOT_UNDER_PRISON_CARE")
    }
  }

  @Nested
  internal inner class PrisonerNotReallyReleased {
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      whenever(releasePrisonerReasonCalculator.calculateReasonForRelease(ArgumentMatchers.any()))
        .thenReturn(
          ReleaseReason(
            Reason.UNKNOWN,
            "some details",
            IN_PRISON,
            UNDER_PRISON_CARE,
            "MDI",
            MovementReason("N"),

          ),
        )
      emitter.convertAndSendWhenSignificant(
        OffenderEvent.builder()
          .eventType("OFFENDER_MOVEMENT-DISCHARGE")
          .offenderIdDisplay("A1234GH")
          .eventDatetime(LocalDateTime.parse("2020-12-04T10:42:43"))
          .build(),
      )
      Mockito.verifyNoInteractions(hmppsEventSnsClient)
      verify(telemetryClient)
        ?.trackEvent(ArgumentMatchers.any(), telemetryAttributesCaptor.capture(), ArgumentMatchers.isNull())
      telemetryAttributes = telemetryAttributesCaptor.value
    }

    @Test
    @DisplayName("will add noms number to telemetry event")
    fun willAddNomsNumberToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
    }

    @Test
    @DisplayName("will add reason to telemetry event")
    fun willAddReasonToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("reason", "UNKNOWN")
    }

    @Test
    @DisplayName("will add occurredAt to telemetry event")
    fun willAddOccurredAtToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-12-04T10:42:43")
    }

    @Test
    @DisplayName("will add details to telemetry event when present")
    fun willAddDetailsToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("details", "some details")
    }

    @Test
    @DisplayName("will add the prisoners current location and status to telemetry")
    fun wilAddThePrisonersCurrentLocationAndStatusToTelemetry() {
      assertThat(telemetryAttributes).containsEntry("currentLocation", "IN_PRISON")
      assertThat(telemetryAttributes).containsEntry("currentPrisonStatus", "UNDER_PRISON_CARE")
    }
  }

  @Nested
  internal inner class MergeRecords {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      whenever(
        mergeRecordDiscriminator.identifyMergedPrisoner(
          ArgumentMatchers.any(),
        ),
      )
        .thenReturn(
          listOf(
            MergeRecordDiscriminator.MergeOutcome("A1234GH", "A1233GP"),
          ),
        )

      emitter.convertAndSendWhenSignificant(
        OffenderEvent.builder()
          .eventType("BOOKING_NUMBER-CHANGED")
          .bookingId(43124234L)
          .eventDatetime(LocalDateTime.parse("2020-12-04T10:42:43"))
          .build(),
      )

      verify(hmppsEventSnsClient, times(1)).publish(publishRequestCaptor.capture())
      payload = publishRequestCaptor.value.message()
      verify(telemetryClient)!!
        .trackEvent(ArgumentMatchers.any(), telemetryAttributesCaptor.capture(), ArgumentMatchers.isNull())
      telemetryAttributes = telemetryAttributesCaptor.value
    }

    @Test
    @DisplayName("will use event datetime for occurred at time")
    fun willUseEventDatetimeForOccurredAtTime() {
      assertThatJson(payload).node("occurredAt").isEqualTo("2020-12-04T10:42:43Z")
    }

    @Test
    @DisplayName("will user current time as publishedAt")
    fun willUserCurrentTimeAsPublishedAt() {
      assertThatJson(payload)
        .node("publishedAt")
        .asString()
        .satisfies(
          Consumer { publishedAt: String? ->
            assertThat(OffsetDateTime.parse(publishedAt))
              .isCloseTo(OffsetDateTime.now(), Assertions.within(10, SECONDS))
          },
        )
    }

    @Test
    @DisplayName("additionalInformation will contain offenderNumber as NOMS number")
    fun additionalInformationWillContainOffenderNumberAsNOMSNumber() {
      assertThatJson(payload).node("additionalInformation.nomsNumber").isEqualTo("A1233GP")
    }

    @Test
    @DisplayName("additionalInformation will contain removed NOMS number")
    fun additionalInformationWillContainRemovedNOMSNumber() {
      assertThatJson(payload).node("additionalInformation.removedNomsNumber").isEqualTo("A1234GH")
    }

    @Test
    @DisplayName("will indicate the reason for a event")
    fun willIndicateTheReasonForAPrisonersEntry() {
      assertThatJson(payload).node("additionalInformation.reason").isEqualTo("MERGE")
    }

    @Test
    @DisplayName("will describe the event as a merge")
    fun willDescribeTheEventAsAMerge() {
      assertThatJson(payload).node("description")
        .isEqualTo("A prisoner has been merged from A1234GH to A1233GP")
    }

    @Test
    @DisplayName("will add retained noms number to telemetry event")
    fun willAddNomsNumberToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1233GP")
    }

    @Test
    @DisplayName("will add removed (merged) noms number to telemetry event")
    fun willAddMergeNumberToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("removedNomsNumber", "A1234GH")
    }

    @Test
    @DisplayName("will add reason to telemetry event")
    fun willAddReasonToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("reason", "MERGE")
    }

    @Test
    @DisplayName("will add occurredAt to telemetry event")
    fun willAddOccurredAtToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-12-04T10:42:43Z")
    }
  }

  @Nested
  internal inner class MergeRecordsMultiple {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      whenever(
        mergeRecordDiscriminator.identifyMergedPrisoner(
          ArgumentMatchers.any(),
        ),
      )
        .thenReturn(
          listOf(
            MergeRecordDiscriminator.MergeOutcome("A1234GH", "A1233GP"),
            MergeRecordDiscriminator.MergeOutcome("A1238GH", "A1233GP"),
            MergeRecordDiscriminator.MergeOutcome("A1239GH", "A1233GP"),
          ),
        )

      emitter.convertAndSendWhenSignificant(
        OffenderEvent.builder()
          .eventType("BOOKING_NUMBER-CHANGED")
          .bookingId(43124234L)
          .eventDatetime(LocalDateTime.parse("2020-12-04T10:42:43"))
          .build(),
      )

      verify(hmppsEventSnsClient, times(3)).publish(publishRequestCaptor.capture())
      payload = publishRequestCaptor.value.message()
      verify(telemetryClient, times(3))!!
        .trackEvent(ArgumentMatchers.any(), telemetryAttributesCaptor.capture(), ArgumentMatchers.isNull())
      telemetryAttributes = telemetryAttributesCaptor.value
    }

    @Test
    @DisplayName("will use event datetime for occurred at time")
    fun willUseEventDatetimeForOccurredAtTime() {
      assertThatJson(payload).node("occurredAt").isEqualTo("2020-12-04T10:42:43Z")
    }

    @Test
    @DisplayName("will user current time as publishedAt")
    fun willUserCurrentTimeAsPublishedAt() {
      assertThatJson(payload)
        .node("publishedAt")
        .asString()
        .satisfies(
          Consumer { publishedAt: String? ->
            assertThat(OffsetDateTime.parse(publishedAt))
              .isCloseTo(OffsetDateTime.now(), Assertions.within(10, SECONDS))
          },
        )
    }

    @Test
    @DisplayName("additionalInformation will contain offenderNumber as NOMS number")
    fun additionalInformationWillContainOffenderNumberAsNOMSNumber() {
      assertThatJson(payload).node("additionalInformation.nomsNumber").isEqualTo("A1233GP")
    }

    @Test
    @DisplayName("will indicate the reason for a event")
    fun willIndicateTheReasonForAPrisonersEntry() {
      assertThatJson(payload).node("additionalInformation.reason").isEqualTo("MERGE")
    }

    @Test
    @DisplayName("will add retained noms number to telemetry event")
    fun willAddNomsNumberToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1233GP")
    }

    @Test
    @DisplayName("will add reason to telemetry event")
    fun willAddReasonToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("reason", "MERGE")
    }

    @Test
    @DisplayName("will add occurredAt to telemetry event")
    fun willAddOccurredAtToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-12-04T10:42:43Z")
    }
  }

  @Nested
  internal inner class CaseNotePublished {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      whenever(offenderEventsProperties.casenotesApiBaseUrl).thenReturn("http://localhost:1234")
      emitter.convertAndSendWhenSignificant(
        OffenderEvent.builder()
          .eventType("OFFENDER_CASE_NOTES-INSERTED")
          .caseNoteType("CHAP")
          .caseNoteSubType("MAIL ROOM")
          .caseNoteId(-12345L)
          .offenderIdDisplay("A1234GH")
          .bookingId(1234L)
          .eventDatetime(LocalDateTime.parse("2022-12-04T10:00:00"))
          .build(),
      )
      verify(hmppsEventSnsClient, times(1)).publish(publishRequestCaptor.capture())
      payload = publishRequestCaptor.value.message()
      verify(telemetryClient)!!
        .trackEvent(ArgumentMatchers.any(), telemetryAttributesCaptor.capture(), ArgumentMatchers.isNull())
      telemetryAttributes = telemetryAttributesCaptor.value
    }

    @Test
    @DisplayName("will use event datetime for occurred at time")
    fun willUseEventDatetimeForOccurredAtTime() {
      assertThatJson(payload).node("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
    }

    @Test
    @DisplayName("will user current time as publishedAt")
    fun willUseCurrentTimeAsPublishedAt() {
      assertThatJson(payload)
        .node("publishedAt")
        .asString()
        .satisfies(
          Consumer { publishedAt: String? ->
            assertThat(OffsetDateTime.parse(publishedAt))
              .isCloseTo(OffsetDateTime.now(), Assertions.within(10, SECONDS))
          },
        )
    }

    @Test
    @DisplayName("person reference will contain offenderNumber as NOMS number")
    fun personReferenceWillContainOffenderNumberAsNOMSNumber() {
      assertThatJson(payload).node("personReference.identifiers").isArray.hasSize(1)
      assertThatJson(payload).node("personReference.identifiers[0].type").isEqualTo("NOMS")
      assertThatJson(payload).node("personReference.identifiers[0].value").isEqualTo("A1234GH")
    }

    @Test
    @DisplayName("additional information will contain the case note type, id, raw type and subtype")
    fun additionalInformationWillContainCaseNoteType() {
      assertThatJson(payload).node("additionalInformation.caseNoteType").isEqualTo("CHAP-MAIL")
      assertThatJson(payload).node("additionalInformation.caseNoteId").isEqualTo("\"-12345\"")
      assertThatJson(payload).node("additionalInformation.type").isEqualTo("\"CHAP\"")
      assertThatJson(payload).node("additionalInformation.subType").isEqualTo("\"MAIL ROOM\"")
    }

    @Test
    @DisplayName("detail url will be set to the offender-case-notes endpoint")
    fun detailUrlWillBeSetToCaseNotesService() {
      assertThatJson(payload).node("detailUrl")
        .isEqualTo("http://localhost:1234/case-notes/A1234GH/-12345")
    }

    @Test
    @DisplayName("will describe the event as a case note")
    fun willDescribeTheEventAsACaseNote() {
      assertThatJson(payload).node("description")
        .isEqualTo("A prison case note has been created or amended")
    }

    @Test
    @DisplayName("will add correct fields to telemetry event")
    fun willAddNomsNumberToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2022-12-04T10:00:00Z")
      assertThat(telemetryAttributes).containsEntry("caseNoteId", "-12345")
      assertThat(telemetryAttributes).containsEntry("caseNoteType", "CHAP-MAIL")
      assertThat(telemetryAttributes).containsEntry("type", "CHAP")
      assertThat(telemetryAttributes).containsEntry("subType", "MAIL ROOM")
    }

    @Test
    @DisplayName("will contain no other telemetry properties")
    fun willContainNoOtherTelemetryProperties() {
      assertThat(telemetryAttributes).containsOnlyKeys(
        "eventType",
        "nomsNumber",
        "occurredAt",
        "publishedAt",
        "caseNoteId",
        "caseNoteType",
        "type",
        "subType",
      )
    }
  }

  @Nested
  internal inner class CaseNotePublishedForDeletedOffender {
    @BeforeEach
    fun setUp() {
      emitter.convertAndSendWhenSignificant(
        OffenderEvent.builder()
          .eventType("OFFENDER_CASE_NOTES-INSERTED")
          .caseNoteType("CHAP")
          .caseNoteSubType("MAIL ROOM")
          .caseNoteId(-12345L)
          .offenderIdDisplay(null)
          .bookingId(1234L)
          .eventDatetime(LocalDateTime.parse("2022-12-04T10:00:00"))
          .build(),
      )
    }

    @Test
    fun `will not publish an event to the sns client`() {
      verifyNoInteractions(hmppsEventSnsClient)
    }

    @Test
    fun `will not create a telemetry event`() {
      verifyNoInteractions(telemetryClient)
    }
  }

  private fun eventMap() = listOf(
    Arguments.of("OFFENDER_MOVEMENT-DISCHARGE", "prison-offender-events.prisoner.released"),
    Arguments.of("OFFENDER_MOVEMENT-RECEPTION", "prison-offender-events.prisoner.received"),
  )

  private fun bookingChangedEventMap() = listOf(
    Arguments.of("BOOKING_NUMBER-CHANGED", "prison-offender-events.prisoner.merged"),
  )
}
