package uk.gov.justice.hmpps.offenderevents.services

import com.amazonaws.services.sns.AmazonSNSAsync
import com.amazonaws.services.sns.model.MessageAttributeValue
import com.amazonaws.services.sns.model.PublishRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import net.javacrumbs.jsonunit.assertj.JsonAssertions
import org.assertj.core.api.Assertions
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness.LENIENT
import uk.gov.justice.hmpps.offenderevents.config.OffenderEventsProperties
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent
import uk.gov.justice.hmpps.offenderevents.services.CurrentLocation.IN_PRISON
import uk.gov.justice.hmpps.offenderevents.services.CurrentLocation.OUTSIDE_PRISON
import uk.gov.justice.hmpps.offenderevents.services.CurrentPrisonStatus.NOT_UNDER_PRISON_CARE
import uk.gov.justice.hmpps.offenderevents.services.CurrentPrisonStatus.UNDER_PRISON_CARE
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.ProbableCause
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.ReceiveReason
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.Source.PRISON
import uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.Reason
import uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.Reason.TEMPORARY_ABSENCE_RELEASE
import uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.ReleaseReason
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit.SECONDS
import java.util.function.Consumer
import java.util.stream.Stream

@TestInstance(PER_CLASS)
@ExtendWith(MockitoExtension::class)
internal class HMPPSDomainEventsEmitterTest {
  private lateinit var emitter: HMPPSDomainEventsEmitter

  @Mock
  private val receivePrisonerReasonCalculator: ReceivePrisonerReasonCalculator? = null

  @Mock
  private val releasePrisonerReasonCalculator: ReleasePrisonerReasonCalculator? = null

  @Mock
  private val mergeRecordDiscriminator: MergeRecordDiscriminator? = null

  @Mock
  private val telemetryClient: TelemetryClient? = null

  @Mock
  private val offenderEventsProperties: OffenderEventsProperties? = null

  @Captor
  private val publishRequestCaptor: ArgumentCaptor<PublishRequest>? = null

  @Captor
  private val telemetryAttributesCaptor: ArgumentCaptor<Map<String, String>>? = null

  private val hmppsQueueService = mock<HmppsQueueService>()
  private var hmppsEventSnsClient = mock<AmazonSNSAsync>()

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
      offenderEventsProperties
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
    Mockito.`when`(receivePrisonerReasonCalculator!!.calculateMostLikelyReasonForPrisonerReceive(ArgumentMatchers.any()))
      .thenReturn(
        ReceiveReason(
          ReceivePrisonerReasonCalculator.Reason.ADMISSION,
          ProbableCause.UNKNOWN,
          PRISON,
          IN_PRISON,
          UNDER_PRISON_CARE,
          "MDI"
        )
      )
    Mockito.`when`(releasePrisonerReasonCalculator!!.calculateReasonForRelease(ArgumentMatchers.any()))
      .thenReturn(
        ReleaseReason(
          TEMPORARY_ABSENCE_RELEASE,
          OUTSIDE_PRISON,
          NOT_UNDER_PRISON_CARE,
          "MDI"
        )
      )
    emitter.convertAndSendWhenSignificant(
      OffenderEvent.builder()
        .eventType(prisonEventType)
        .offenderIdDisplay("A1234GH")
        .eventDatetime(LocalDateTime.now())
        .build()
    )

    Mockito.verify(hmppsEventSnsClient, times(1)).publishAsync(publishRequestCaptor!!.capture())
    val payload = publishRequestCaptor.value.message
    val messageAttributes = publishRequestCaptor.value.messageAttributes
    JsonAssertions.assertThatJson(payload).node("eventType").isEqualTo(eventType)
    JsonAssertions.assertThatJson(payload).node("version").isEqualTo(1)
    Assertions.assertThat(
      messageAttributes["eventType"]
    )
      .isEqualTo(MessageAttributeValue().withStringValue(eventType).withDataType("String"))
    Mockito.verify(telemetryClient)!!
      .trackEvent(ArgumentMatchers.eq(eventType), ArgumentMatchers.anyMap(), ArgumentMatchers.isNull())
  }

  @ParameterizedTest
  @MethodSource("bookingChangedEventMap")
  @DisplayName("Will send to topic for these events")
  @MockitoSettings(strictness = LENIENT)
  fun willSendToTopicForBookingChangedEvent(prisonEventType: String, eventType: String) {
    Mockito.`when`(mergeRecordDiscriminator!!.identifyMergedPrisoner(ArgumentMatchers.eq(43124234L)))
      .thenReturn(
        listOf(
          MergeRecordDiscriminator.MergeOutcome("A1234GH", "A1233GP")
        )
      )

    emitter.convertAndSendWhenSignificant(
      OffenderEvent.builder()
        .eventType(prisonEventType)
        .offenderIdDisplay("A1234GH")
        .bookingId(43124234L)
        .eventDatetime(LocalDateTime.now())
        .build()
    )

    Mockito.verify(hmppsEventSnsClient, times(1)).publishAsync(publishRequestCaptor!!.capture())
    val payload = publishRequestCaptor.value.message
    val messageAttributes = publishRequestCaptor.value.messageAttributes
    JsonAssertions.assertThatJson(payload).node("eventType").isEqualTo(eventType)
    JsonAssertions.assertThatJson(payload).node("version").isEqualTo(1)
    Assertions.assertThat(
      messageAttributes["eventType"]
    )
      .isEqualTo(MessageAttributeValue().withStringValue(eventType).withDataType("String"))
    Mockito.verify(telemetryClient)!!
      .trackEvent(ArgumentMatchers.eq(eventType), ArgumentMatchers.anyMap(), ArgumentMatchers.isNull())
  }

  @Nested
  internal inner class PrisonerReceived {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      Mockito.`when`(
        receivePrisonerReasonCalculator!!.calculateMostLikelyReasonForPrisonerReceive(
          ArgumentMatchers.any()
        )
      )
        .thenReturn(
          ReceiveReason(
            ReceivePrisonerReasonCalculator.Reason.ADMISSION,
            ProbableCause.RECALL,
            PRISON,
            "some details",
            IN_PRISON,
            UNDER_PRISON_CARE,
            "MDI"
          )
        )
      emitter.convertAndSendWhenSignificant(
        OffenderEvent.builder()
          .eventType("OFFENDER_MOVEMENT-RECEPTION")
          .offenderIdDisplay("A1234GH")
          .eventDatetime(LocalDateTime.parse("2020-12-04T10:42:43"))
          .build()
      )

      Mockito.verify(hmppsEventSnsClient, times(1)).publishAsync(publishRequestCaptor!!.capture())
      payload = publishRequestCaptor.value.message
      Mockito.verify(telemetryClient)!!
        .trackEvent(ArgumentMatchers.any(), telemetryAttributesCaptor!!.capture(), ArgumentMatchers.isNull())
      telemetryAttributes = telemetryAttributesCaptor.value
    }

    @Test
    @DisplayName("will use event datetime for occurred at time")
    fun willUseEventDatetimeForOccurredAtTime() {
      JsonAssertions.assertThatJson(payload).node("occurredAt").isEqualTo("2020-12-04T10:42:43Z")
    }

    @Test
    @DisplayName("will user current time as publishedAt")
    fun willUserCurrentTimeAsPublishedAt() {
      JsonAssertions.assertThatJson(payload)
        .node("publishedAt")
        .asString()
        .satisfies(
          Consumer { publishedAt: String? ->
            Assertions.assertThat(OffsetDateTime.parse(publishedAt))
              .isCloseTo(OffsetDateTime.now(), Assertions.within(10, SECONDS))
          }
        )
    }

    @Test
    @DisplayName("additionalInformation will contain offenderNumber as NOMS number")
    fun additionalInformationWillContainOffenderNumberAsNOMSNumber() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.nomsNumber").isEqualTo("A1234GH")
    }

    @Test
    @DisplayName("will describe the prisoners last (or current) location")
    fun additionalInformationWillContainPrisonId() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.prisonId").isEqualTo("MDI")
    }

    @Test
    @DisplayName("will indicate the reason for a prisoners entry")
    fun willIndicateTheReasonForAPrisonersEntry() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.reason").isEqualTo("ADMISSION")
    }

    @Test
    @DisplayName("will indicate the probable cause for a prisoners entry")
    fun willIndicateTheProbableCauseForAPrisonersEntry() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.probableCause").isEqualTo("RECALL")
    }

    @Test
    @DisplayName("will describe the event as a receive")
    fun willDescribeTheEventAsAReceive() {
      JsonAssertions.assertThatJson(payload).node("description")
        .isEqualTo("A prisoner has been received into prison")
    }

    @Test
    @DisplayName("will describe the prisoners current location and status")
    fun willDescribeThePrisonersCurrentLocation() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.currentLocation").isEqualTo("IN_PRISON")
      JsonAssertions.assertThatJson(payload).node("additionalInformation.currentPrisonStatus")
        .isEqualTo("UNDER_PRISON_CARE")
    }

    @Test
    @DisplayName("will add noms number to telemetry event")
    fun willAddNomsNumberToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
    }

    @Test
    @DisplayName("will add reason to telemetry event")
    fun willAddReasonToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("reason", "ADMISSION")
    }

    @Test
    @DisplayName("will add probable cause to telemetry event")
    fun willAddProbableCauseToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("probableCause", "RECALL")
    }

    @Test
    @DisplayName("will add occurredAt to telemetry event")
    fun willAddOccurredAtToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-12-04T10:42:43Z")
    }

    @Test
    @DisplayName("will add source to telemetry event")
    fun willAddSourceToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("source", "PRISON")
    }

    @Test
    @DisplayName("will add details to telemetry event when present")
    fun willAddDetailsToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("details", "some details")
    }

    @Test
    @DisplayName("will add the prisoners current location and status to telemetry")
    fun wilAddThePrisonersCurrentLocationAndStatusToTelemetry() {
      Assertions.assertThat(telemetryAttributes).containsEntry("currentLocation", "IN_PRISON")
      Assertions.assertThat(telemetryAttributes).containsEntry("currentPrisonStatus", "UNDER_PRISON_CARE")
    }
  }

  @Nested
  internal inner class PrisonerNotReallyReceived {
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      Mockito.`when`(receivePrisonerReasonCalculator!!.calculateMostLikelyReasonForPrisonerReceive(ArgumentMatchers.any()))
        .thenReturn(
          ReceiveReason(
            ReceivePrisonerReasonCalculator.Reason.ADMISSION,
            ProbableCause.UNKNOWN,
            PRISON,
            "some details",
            OUTSIDE_PRISON,
            NOT_UNDER_PRISON_CARE,
            "MDI"
          )
        )
      emitter.convertAndSendWhenSignificant(
        OffenderEvent.builder()
          .eventType("OFFENDER_MOVEMENT-RECEPTION")
          .offenderIdDisplay("A1234GH")
          .eventDatetime(LocalDateTime.parse("2020-12-04T10:42:43"))
          .build()
      )
      Mockito.verifyNoInteractions(hmppsEventSnsClient)
      Mockito.verify(telemetryClient)!!
        .trackEvent(ArgumentMatchers.any(), telemetryAttributesCaptor!!.capture(), ArgumentMatchers.isNull())
      telemetryAttributes = telemetryAttributesCaptor.value
    }

    @Test
    @DisplayName("will add noms number to telemetry event")
    fun willAddNomsNumberToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
    }

    @Test
    @DisplayName("will add reason to telemetry event")
    fun willAddReasonToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("reason", "ADMISSION")
    }

    @Test
    @DisplayName("will add occurredAt to telemetry event")
    fun willAddOccurredAtToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-12-04T10:42:43")
    }

    @Test
    @DisplayName("will add details to telemetry event when present")
    fun willAddDetailsToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("details", "some details")
    }

    @Test
    @DisplayName("will add the prisoners current location and status to telemetry")
    fun wilAddThePrisonersCurrentLocationAndStatusToTelemetry() {
      Assertions.assertThat(telemetryAttributes).containsEntry("currentLocation", "OUTSIDE_PRISON")
      Assertions.assertThat(telemetryAttributes).containsEntry("currentPrisonStatus", "NOT_UNDER_PRISON_CARE")
    }
  }

  @Nested
  internal inner class PrisonerReleased {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      Mockito.`when`(releasePrisonerReasonCalculator!!.calculateReasonForRelease(ArgumentMatchers.any()))
        .thenReturn(
          ReleaseReason(
            TEMPORARY_ABSENCE_RELEASE,
            "some details",
            OUTSIDE_PRISON,
            NOT_UNDER_PRISON_CARE,
            "MDI"
          )
        )
      emitter.convertAndSendWhenSignificant(
        OffenderEvent.builder()
          .eventType("OFFENDER_MOVEMENT-DISCHARGE")
          .offenderIdDisplay("A1234GH")
          .eventDatetime(LocalDateTime.parse("2020-07-04T10:42:43"))
          .build()
      )
      Mockito.verify(hmppsEventSnsClient, times(1)).publishAsync(publishRequestCaptor!!.capture())
      payload = publishRequestCaptor.value.message
      Mockito.verify(telemetryClient)!!
        .trackEvent(ArgumentMatchers.any(), telemetryAttributesCaptor!!.capture(), ArgumentMatchers.isNull())
      telemetryAttributes = telemetryAttributesCaptor.value
    }

    @Test
    @DisplayName("will use event datetime for occurred at time")
    fun willUseEventDatetimeForOccurredAtTime() {
      JsonAssertions.assertThatJson(payload).node("occurredAt").isEqualTo("2020-07-04T10:42:43+01:00")
    }

    @Test
    @DisplayName("will user current time as publishedAt")
    fun willUserCurrentTimeAsPublishedAt() {
      JsonAssertions.assertThatJson(payload)
        .node("publishedAt")
        .asString()
        .satisfies(
          Consumer { publishedAt: String? ->
            Assertions.assertThat(OffsetDateTime.parse(publishedAt))
              .isCloseTo(OffsetDateTime.now(), Assertions.within(10, SECONDS))
          }
        )
    }

    @Test
    @DisplayName("additionalInformation will contain offenderNumber as NOMS number")
    fun additionalInformationWillContainOffenderNumberAsNOMSNumber() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.nomsNumber").isEqualTo("A1234GH")
    }

    @Test
    @DisplayName("will describe the prisoners last (or current) location")
    fun additionalInformationWillContainPrisonId() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.prisonId").isEqualTo("MDI")
    }

    @Test
    @DisplayName("will indicate the reason for a prisoners exit")
    fun willIndicateTheReasonForAPrisonersExit() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.reason")
        .isEqualTo("TEMPORARY_ABSENCE_RELEASE")
    }

    @Test
    @DisplayName("will describe the event as a release")
    fun willDescribeTheEventAsAReceive() {
      JsonAssertions.assertThatJson(payload).node("description")
        .isEqualTo("A prisoner has been released from prison")
    }

    @Test
    @DisplayName("will describe the prisoners current location and status")
    fun willDescribeThePrisonersCurrentLocation() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.currentLocation")
        .isEqualTo("OUTSIDE_PRISON")
      JsonAssertions.assertThatJson(payload)
        .node("additionalInformation.currentPrisonStatus")
        .isEqualTo("NOT_UNDER_PRISON_CARE")
    }

    @Test
    @DisplayName("will add noms number to telemetry event")
    fun willAddNomsNumberToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
    }

    @Test
    @DisplayName("will add reason to telemetry event")
    fun willAddReasonToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("reason", "TEMPORARY_ABSENCE_RELEASE")
    }

    @Test
    @DisplayName("will add occurredAt to telemetry event")
    fun willAddOccurredAtToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-07-04T10:42:43+01:00")
    }

    @Test
    @DisplayName("will add details to telemetry event when present")
    fun willAddDetailsToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("details", "some details")
    }

    @Test
    @DisplayName("source will be absent from event and telemetry when not present")
    fun sourceWillBeAbsentFromEventAndTelemetryWhenNotPresent() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.source").isAbsent()
      Assertions.assertThat(telemetryAttributes).doesNotContainKey("source")
    }

    @Test
    @DisplayName("will add the prisoners current location and status to telemetry")
    fun wilAddThePrisonersCurrentLocationAndStatusToTelemetry() {
      Assertions.assertThat(telemetryAttributes).containsEntry("currentLocation", "OUTSIDE_PRISON")
      Assertions.assertThat(telemetryAttributes).containsEntry("currentPrisonStatus", "NOT_UNDER_PRISON_CARE")
    }
  }

  @Nested
  internal inner class PrisonerNotReallyReleased {
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      Mockito.`when`(releasePrisonerReasonCalculator!!.calculateReasonForRelease(ArgumentMatchers.any()))
        .thenReturn(
          ReleaseReason(
            Reason.UNKNOWN, "some details", IN_PRISON,
            UNDER_PRISON_CARE, "MDI"
          )
        )
      emitter.convertAndSendWhenSignificant(
        OffenderEvent.builder()
          .eventType("OFFENDER_MOVEMENT-DISCHARGE")
          .offenderIdDisplay("A1234GH")
          .eventDatetime(LocalDateTime.parse("2020-12-04T10:42:43"))
          .build()
      )
      Mockito.verifyNoInteractions(hmppsEventSnsClient)
      Mockito.verify(telemetryClient)
        ?.trackEvent(ArgumentMatchers.any(), telemetryAttributesCaptor!!.capture(), ArgumentMatchers.isNull())
      telemetryAttributes = telemetryAttributesCaptor!!.value
    }

    @Test
    @DisplayName("will add noms number to telemetry event")
    fun willAddNomsNumberToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
    }

    @Test
    @DisplayName("will add reason to telemetry event")
    fun willAddReasonToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("reason", "UNKNOWN")
    }

    @Test
    @DisplayName("will add occurredAt to telemetry event")
    fun willAddOccurredAtToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-12-04T10:42:43")
    }

    @Test
    @DisplayName("will add details to telemetry event when present")
    fun willAddDetailsToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("details", "some details")
    }

    @Test
    @DisplayName("will add the prisoners current location and status to telemetry")
    fun wilAddThePrisonersCurrentLocationAndStatusToTelemetry() {
      Assertions.assertThat(telemetryAttributes).containsEntry("currentLocation", "IN_PRISON")
      Assertions.assertThat(telemetryAttributes).containsEntry("currentPrisonStatus", "UNDER_PRISON_CARE")
    }
  }

  @Nested
  internal inner class MergeRecords {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      Mockito.`when`(
        mergeRecordDiscriminator!!.identifyMergedPrisoner(
          ArgumentMatchers.any()
        )
      )
        .thenReturn(
          listOf(
            MergeRecordDiscriminator.MergeOutcome("A1234GH", "A1233GP")
          )
        )

      emitter.convertAndSendWhenSignificant(
        OffenderEvent.builder()
          .eventType("BOOKING_NUMBER-CHANGED")
          .bookingId(43124234L)
          .eventDatetime(LocalDateTime.parse("2020-12-04T10:42:43"))
          .build()
      )

      Mockito.verify(hmppsEventSnsClient, times(1)).publishAsync(publishRequestCaptor!!.capture())
      payload = publishRequestCaptor.value.message
      Mockito.verify(telemetryClient)!!
        .trackEvent(ArgumentMatchers.any(), telemetryAttributesCaptor!!.capture(), ArgumentMatchers.isNull())
      telemetryAttributes = telemetryAttributesCaptor.value
    }

    @Test
    @DisplayName("will use event datetime for occurred at time")
    fun willUseEventDatetimeForOccurredAtTime() {
      JsonAssertions.assertThatJson(payload).node("occurredAt").isEqualTo("2020-12-04T10:42:43Z")
    }

    @Test
    @DisplayName("will user current time as publishedAt")
    fun willUserCurrentTimeAsPublishedAt() {
      JsonAssertions.assertThatJson(payload)
        .node("publishedAt")
        .asString()
        .satisfies(
          Consumer { publishedAt: String? ->
            Assertions.assertThat(OffsetDateTime.parse(publishedAt))
              .isCloseTo(OffsetDateTime.now(), Assertions.within(10, SECONDS))
          }
        )
    }

    @Test
    @DisplayName("additionalInformation will contain offenderNumber as NOMS number")
    fun additionalInformationWillContainOffenderNumberAsNOMSNumber() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.nomsNumber").isEqualTo("A1233GP")
    }

    @Test
    @DisplayName("additionalInformation will contain removed NOMS number")
    fun additionalInformationWillContainRemovedNOMSNumber() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.removedNomsNumber").isEqualTo("A1234GH")
    }

    @Test
    @DisplayName("will indicate the reason for a event")
    fun willIndicateTheReasonForAPrisonersEntry() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.reason").isEqualTo("MERGE")
    }

    @Test
    @DisplayName("will describe the event as a merge")
    fun willDescribeTheEventAsAMerge() {
      JsonAssertions.assertThatJson(payload).node("description")
        .isEqualTo("A prisoner has been merged from A1234GH to A1233GP")
    }

    @Test
    @DisplayName("will add retained noms number to telemetry event")
    fun willAddNomsNumberToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1233GP")
    }

    @Test
    @DisplayName("will add removed (merged) noms number to telemetry event")
    fun willAddMergeNumberToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("removedNomsNumber", "A1234GH")
    }

    @Test
    @DisplayName("will add reason to telemetry event")
    fun willAddReasonToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("reason", "MERGE")
    }

    @Test
    @DisplayName("will add occurredAt to telemetry event")
    fun willAddOccurredAtToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-12-04T10:42:43Z")
    }
  }

  @Nested
  internal inner class MergeRecordsMultiple {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      Mockito.`when`(
        mergeRecordDiscriminator!!.identifyMergedPrisoner(
          ArgumentMatchers.any()
        )
      )
        .thenReturn(
          listOf(
            MergeRecordDiscriminator.MergeOutcome("A1234GH", "A1233GP"),
            MergeRecordDiscriminator.MergeOutcome("A1238GH", "A1233GP"),
            MergeRecordDiscriminator.MergeOutcome("A1239GH", "A1233GP")
          )
        )

      emitter.convertAndSendWhenSignificant(
        OffenderEvent.builder()
          .eventType("BOOKING_NUMBER-CHANGED")
          .bookingId(43124234L)
          .eventDatetime(LocalDateTime.parse("2020-12-04T10:42:43"))
          .build()
      )

      Mockito.verify(hmppsEventSnsClient, times(3)).publishAsync(publishRequestCaptor!!.capture())
      payload = publishRequestCaptor.value.message
      Mockito.verify(telemetryClient, times(3))!!
        .trackEvent(ArgumentMatchers.any(), telemetryAttributesCaptor!!.capture(), ArgumentMatchers.isNull())
      telemetryAttributes = telemetryAttributesCaptor.value
    }

    @Test
    @DisplayName("will use event datetime for occurred at time")
    fun willUseEventDatetimeForOccurredAtTime() {
      JsonAssertions.assertThatJson(payload).node("occurredAt").isEqualTo("2020-12-04T10:42:43Z")
    }

    @Test
    @DisplayName("will user current time as publishedAt")
    fun willUserCurrentTimeAsPublishedAt() {
      JsonAssertions.assertThatJson(payload)
        .node("publishedAt")
        .asString()
        .satisfies(
          Consumer { publishedAt: String? ->
            Assertions.assertThat(OffsetDateTime.parse(publishedAt))
              .isCloseTo(OffsetDateTime.now(), Assertions.within(10, SECONDS))
          }
        )
    }

    @Test
    @DisplayName("additionalInformation will contain offenderNumber as NOMS number")
    fun additionalInformationWillContainOffenderNumberAsNOMSNumber() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.nomsNumber").isEqualTo("A1233GP")
    }

    @Test
    @DisplayName("will indicate the reason for a event")
    fun willIndicateTheReasonForAPrisonersEntry() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.reason").isEqualTo("MERGE")
    }

    @Test
    @DisplayName("will add retained noms number to telemetry event")
    fun willAddNomsNumberToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1233GP")
    }

    @Test
    @DisplayName("will add reason to telemetry event")
    fun willAddReasonToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("reason", "MERGE")
    }

    @Test
    @DisplayName("will add occurredAt to telemetry event")
    fun willAddOccurredAtToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-12-04T10:42:43Z")
    }
  }

  @Nested
  internal inner class CaseNotePublished {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      whenever(offenderEventsProperties!!.casenotesApiBaseUrl).thenReturn("http://localhost:1234")
      emitter.convertAndSendWhenSignificant(
        OffenderEvent.builder()
          .eventType("GEN-OSE")
          .caseNoteId(-12345L)
          .offenderIdDisplay("A1234GH")
          .eventDatetime(LocalDateTime.parse("2020-07-04T10:42:43"))
          .build()
      )
      Mockito.verify(hmppsEventSnsClient, times(1)).publishAsync(publishRequestCaptor!!.capture())
      payload = publishRequestCaptor.value.message
      Mockito.verify(telemetryClient)!!
        .trackEvent(ArgumentMatchers.any(), telemetryAttributesCaptor!!.capture(), ArgumentMatchers.isNull())
      telemetryAttributes = telemetryAttributesCaptor.value
    }

    @Test
    @DisplayName("will use event datetime for occurred at time")
    fun willUseEventDatetimeForOccurredAtTime() {
      JsonAssertions.assertThatJson(payload).node("occurredAt").isEqualTo("2020-07-04T10:42:43+01:00")
    }

    @Test
    @DisplayName("will user current time as publishedAt")
    fun willUseCurrentTimeAsPublishedAt() {
      JsonAssertions.assertThatJson(payload)
        .node("publishedAt")
        .asString()
        .satisfies(
          Consumer { publishedAt: String? ->
            Assertions.assertThat(OffsetDateTime.parse(publishedAt))
              .isCloseTo(OffsetDateTime.now(), Assertions.within(10, SECONDS))
          }
        )
    }

    @Test
    @DisplayName("person reference will contain offenderNumber as NOMS number")
    fun personReferenceWillContainOffenderNumberAsNOMSNumber() {
      JsonAssertions.assertThatJson(payload).node("personReference.identifiers").isArray.hasSize(1)
      JsonAssertions.assertThatJson(payload).node("personReference.identifiers[0].type").isEqualTo("NOMS")
      JsonAssertions.assertThatJson(payload).node("personReference.identifiers[0].value").isEqualTo("A1234GH")
    }

    @Test
    @DisplayName("additional information will contain the case note type")
    fun additionalInformationWillContainCaseNoteType() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.caseNoteType").isEqualTo("GEN-OSE")
    }

    @Test
    @DisplayName("additional information will contain the case note id")
    fun additionalInformationWillContainCaseNoteId() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.caseNoteId").isEqualTo("\"-12345\"")
    }

    @Test
    @DisplayName("detail url will be set to the offender-case-notes endpoint")
    fun detailUrlWillBeSetToCaseNotesService() {
      JsonAssertions.assertThatJson(payload).node("detailUrl")
        .isEqualTo("http://localhost:1234/case-notes/A1234GH/-12345")
    }

    @Test
    @DisplayName("will describe the event as a case note")
    fun willDescribeTheEventAsACaseNote() {
      JsonAssertions.assertThatJson(payload).node("description")
        .isEqualTo("A prison case note has been created or amended")
    }

    @Test
    @DisplayName("will add noms number to telemetry event")
    fun willAddNomsNumberToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
    }

    @Test
    @DisplayName("will add occurredAt to telemetry event")
    fun willAddOccurredAtToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-07-04T10:42:43+01:00")
    }

    @Test
    @DisplayName("will add caseNoteId to telemetry event")
    fun willAddCaseNoteIdToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("caseNoteId", "-12345")
    }

    @Test
    @DisplayName("will add caseNoteType to telemetry event")
    fun willAddCaseNoteTypeToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("caseNoteType", "GEN-OSE")
    }

    @Test
    @DisplayName("will contain no other telemetry properties")
    fun willContainNoOtherTelemetryProperties() {
      Assertions.assertThat(telemetryAttributes).containsOnlyKeys("eventType", "nomsNumber", "occurredAt", "caseNoteId", "caseNoteType")
    }
  }

  @Nested
  internal inner class CaseNotePublishedNew {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      whenever(offenderEventsProperties!!.casenotesApiBaseUrl).thenReturn("http://localhost:1234")
      emitter.convertAndSendWhenSignificant(
        OffenderEvent.builder()
          .eventType("OFFENDER_CASE_NOTES-INSERTED")
          .caseNoteType("CHAP")
          .caseNoteSubType("FAITH")
          .caseNoteId(-12345L)
          .offenderIdDisplay("A1234GH")
          .bookingId(1234L)
          .eventDatetime(LocalDateTime.parse("2022-12-04T10:00:00"))
          .build()
      )
      Mockito.verify(hmppsEventSnsClient, times(1)).publishAsync(publishRequestCaptor!!.capture())
      payload = publishRequestCaptor.value.message
      Mockito.verify(telemetryClient)!!
        .trackEvent(ArgumentMatchers.any(), telemetryAttributesCaptor!!.capture(), ArgumentMatchers.isNull())
      telemetryAttributes = telemetryAttributesCaptor.value
    }

    @Test
    @DisplayName("will use event datetime for occurred at time")
    fun willUseEventDatetimeForOccurredAtTime() {
      JsonAssertions.assertThatJson(payload).node("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
    }

    @Test
    @DisplayName("will user current time as publishedAt")
    fun willUseCurrentTimeAsPublishedAt() {
      JsonAssertions.assertThatJson(payload)
        .node("publishedAt")
        .asString()
        .satisfies(
          Consumer { publishedAt: String? ->
            Assertions.assertThat(OffsetDateTime.parse(publishedAt))
              .isCloseTo(OffsetDateTime.now(), Assertions.within(10, SECONDS))
          }
        )
    }

    @Test
    @DisplayName("person reference will contain offenderNumber as NOMS number")
    fun personReferenceWillContainOffenderNumberAsNOMSNumber() {
      JsonAssertions.assertThatJson(payload).node("personReference.identifiers").isArray.hasSize(1)
      JsonAssertions.assertThatJson(payload).node("personReference.identifiers[0].type").isEqualTo("NOMS")
      JsonAssertions.assertThatJson(payload).node("personReference.identifiers[0].value").isEqualTo("A1234GH")
    }

    @Test
    @DisplayName("additional information will contain the case note type, id, raw type and subtype")
    fun additionalInformationWillContainCaseNoteType() {
      JsonAssertions.assertThatJson(payload).node("additionalInformation.caseNoteType").isEqualTo("CHAP-FAITH")
      JsonAssertions.assertThatJson(payload).node("additionalInformation.caseNoteId").isEqualTo("\"-12345\"")
      JsonAssertions.assertThatJson(payload).node("additionalInformation.type").isEqualTo("\"CHAP\"")
      JsonAssertions.assertThatJson(payload).node("additionalInformation.subType").isEqualTo("\"FAITH\"")
    }

    @Test
    @DisplayName("detail url will be set to the offender-case-notes endpoint")
    fun detailUrlWillBeSetToCaseNotesService() {
      JsonAssertions.assertThatJson(payload).node("detailUrl")
        .isEqualTo("http://localhost:1234/case-notes/A1234GH/-12345")
    }

    @Test
    @DisplayName("will describe the event as a case note")
    fun willDescribeTheEventAsACaseNote() {
      JsonAssertions.assertThatJson(payload).node("description")
        .isEqualTo("A prison case note has been created or amended")
    }

    @Test
    @DisplayName("will add correct fields to telemetry event")
    fun willAddNomsNumberToTelemetryEvent() {
      Assertions.assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
      Assertions.assertThat(telemetryAttributes).containsEntry("occurredAt", "2022-12-04T10:00:00Z")
      Assertions.assertThat(telemetryAttributes).containsEntry("caseNoteId", "-12345")
      Assertions.assertThat(telemetryAttributes).containsEntry("caseNoteType", "CHAP-FAITH")
      Assertions.assertThat(telemetryAttributes).containsEntry("type", "CHAP")
      Assertions.assertThat(telemetryAttributes).containsEntry("subType", "FAITH")
    }

    @Test
    @DisplayName("will contain no other telemetry properties")
    fun willContainNoOtherTelemetryProperties() {
      Assertions.assertThat(telemetryAttributes).containsOnlyKeys("eventType", "nomsNumber", "occurredAt", "caseNoteId", "caseNoteType", "type", "subType")
    }
  }

  @Suppress("unused")
  private fun eventMap(): Stream<Arguments> {
    return Stream.of(
      Arguments.of("OFFENDER_MOVEMENT-DISCHARGE", "prison-offender-events.prisoner.released"),
      Arguments.of("OFFENDER_MOVEMENT-RECEPTION", "prison-offender-events.prisoner.received")
    )
  }

  @Suppress("unused")
  private fun bookingChangedEventMap(): Stream<Arguments> {
    return Stream.of(
      Arguments.of("BOOKING_NUMBER-CHANGED", "prison-offender-events.prisoner.merged")
    )
  }
}
