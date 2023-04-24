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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness.LENIENT
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.hmpps.offenderevents.config.OffenderEventsProperties
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit.SECONDS
import java.util.function.Consumer

@SpringBootTest(classes = [JacksonAutoConfiguration::class])
internal class HMPPSDomainEventsEmitterTest {
  private lateinit var emitter: HMPPSDomainEventsEmitter

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  private val receivePrisonerReasonCalculator: ReceivePrisonerReasonCalculator = mock()
  private val releasePrisonerReasonCalculator: ReleasePrisonerReasonCalculator = mock()
  private val mergeRecordDiscriminator: MergeRecordDiscriminator = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val offenderEventsProperties: OffenderEventsProperties = mock()

  private val hmppsQueueService: HmppsQueueService = mock()
  private val hmppsEventSnsClient: SnsAsyncClient = mock()

  @BeforeEach
  fun setUp() {
    whenever(hmppsQueueService.findByTopicId("hmppseventtopic"))
      .thenReturn(HmppsTopic("hmppseventtopic", "sometopicarn", hmppsEventSnsClient))

    emitter = HMPPSDomainEventsEmitter(
      hmppsQueueService,
      objectMapper,
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
    emitter.convertAndSendWhenSignificant("BALANCE_UPDATED", "")
    verifyNoInteractions(hmppsEventSnsClient)
  }

  @ParameterizedTest
  @MethodSource("eventMap")
  @DisplayName("Will send to topic for these events")
  fun willSendToTopicForTheseEvents(prisonEventType: String, eventType: String) {
    whenever(receivePrisonerReasonCalculator.calculateMostLikelyReasonForPrisonerReceive(any()))
      .thenReturn(
        ReceiveReason(
          reason = ReceivePrisonerReasonCalculator.Reason.ADMISSION,
          probableCause = ProbableCause.UNKNOWN,
          source = PRISON,
          currentLocation = IN_PRISON,
          currentPrisonStatus = UNDER_PRISON_CARE,
          prisonId = "MDI",
          nomisMovementReason = ReceivePrisonerReasonCalculator.MovementReason("N"),
        ),
      )
    whenever(releasePrisonerReasonCalculator.calculateReasonForRelease(any()))
      .thenReturn(
        ReleaseReason(
          reason = TEMPORARY_ABSENCE_RELEASE,
          currentLocation = OUTSIDE_PRISON,
          currentPrisonStatus = NOT_UNDER_PRISON_CARE,
          prisonId = "MDI",
          nomisMovementReason = MovementReason("N"),
        ),
      )
    emitter.convertAndSendWhenSignificant(
      prisonEventType,
      """{ "offenderIdDisplay": "A1234GH", "eventDatetime": "${LocalDateTime.now()}" }""",
    )

    argumentCaptor<PublishRequest>().apply {
      verify(hmppsEventSnsClient, times(1)).publish(capture())
      val payload = firstValue.message()
      val messageAttributes = firstValue.messageAttributes()
      assertThatJson(payload).node("eventType").isEqualTo(eventType)
      assertThatJson(payload).node("version").isEqualTo(1)
      assertThat(
        messageAttributes["eventType"],
      )
        .isEqualTo(MessageAttributeValue.builder().stringValue(eventType).dataType("String").build())
      verify(telemetryClient).trackEvent(ArgumentMatchers.eq(eventType), ArgumentMatchers.anyMap(), isNull())
    }
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
      prisonEventType,
      """
        {
           "offenderIdDisplay": "A1234GH",
           "bookingId": 43124234,
           "eventDatetime": "${LocalDateTime.now()}" 
        } 
      """.trimIndent(),
    )

    argumentCaptor<PublishRequest>().apply {
      verify(hmppsEventSnsClient, times(1)).publish(capture())
      val payload = firstValue.message()
      val messageAttributes = firstValue.messageAttributes()
      assertThatJson(payload).node("eventType").isEqualTo(eventType)
      assertThatJson(payload).node("version").isEqualTo(1)
      assertThat(
        messageAttributes["eventType"],
      )
        .isEqualTo(MessageAttributeValue.builder().stringValue(eventType).dataType("String").build())
      verify(telemetryClient)
        .trackEvent(ArgumentMatchers.eq(eventType), ArgumentMatchers.anyMap(), isNull())
    }
  }

  @Nested
  internal inner class PrisonerReceived {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      whenever(
        receivePrisonerReasonCalculator.calculateMostLikelyReasonForPrisonerReceive(
          any(),
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
        "OFFENDER_MOVEMENT-RECEPTION",
        """
        {
           "offenderIdDisplay": "A1234GH",
           "eventDatetime": "${LocalDateTime.parse("2020-12-04T10:42:43")}" 
        } 
        """.trimIndent(),
      )

      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient, times(1)).publish(capture())
        payload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient)
          .trackEvent(any(), capture(), isNull())
        telemetryAttributes = firstValue
      }
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
      whenever(receivePrisonerReasonCalculator.calculateMostLikelyReasonForPrisonerReceive(any()))
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
        "OFFENDER_MOVEMENT-RECEPTION",
        """
        {
           "offenderIdDisplay": "A1234GH",
           "eventDatetime": "${LocalDateTime.parse("2020-12-04T10:42:43")}" 
        } 
        """.trimIndent(),
      )
      Mockito.verifyNoInteractions(hmppsEventSnsClient)

      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient)
          .trackEvent(any(), capture(), isNull())
        telemetryAttributes = firstValue
      }
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
      whenever(releasePrisonerReasonCalculator.calculateReasonForRelease(any()))
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
        "OFFENDER_MOVEMENT-DISCHARGE",
        """
        {
           "offenderIdDisplay": "A1234GH",
           "eventDatetime": "${LocalDateTime.parse("2020-07-04T10:42:43")}" 
        } 
        """.trimIndent(),
      )
      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient, times(1)).publish(capture())
        payload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient).trackEvent(any(), capture(), isNull())
        telemetryAttributes = firstValue
      }
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
      whenever(releasePrisonerReasonCalculator.calculateReasonForRelease(any()))
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
        "OFFENDER_MOVEMENT-DISCHARGE",
        """
        {
           "offenderIdDisplay": "A1234GH",
           "eventDatetime": "${LocalDateTime.parse("2020-12-04T10:42:43")}" 
        } 
        """.trimIndent(),
      )
      Mockito.verifyNoInteractions(hmppsEventSnsClient)
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient).trackEvent(any(), capture(), isNull())
        telemetryAttributes = firstValue
      }
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
          any(),
        ),
      )
        .thenReturn(
          listOf(
            MergeRecordDiscriminator.MergeOutcome("A1234GH", "A1233GP"),
          ),
        )

      emitter.convertAndSendWhenSignificant(
        "BOOKING_NUMBER-CHANGED",
        """
        {
           "bookingId": 43124234,
           "eventDatetime": "${LocalDateTime.parse("2020-12-04T10:42:43")}" 
        } 
        """.trimIndent(),
      )

      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient, times(1)).publish(capture())
        payload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient).trackEvent(any(), capture(), isNull())
        telemetryAttributes = firstValue
      }
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
          any(),
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
        "BOOKING_NUMBER-CHANGED",
        """
        {
           "bookingId": 43124234,
           "eventDatetime": "${LocalDateTime.parse("2020-12-04T10:42:43")}" 
        } 
        """.trimIndent(),
      )

      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient, times(3)).publish(capture())
        payload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient, times(3)).trackEvent(any(), capture(), isNull())
        telemetryAttributes = firstValue
      }
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
        "OFFENDER_CASE_NOTES-INSERTED",
        """
        {
           "offenderIdDisplay": "A1234GH",
           "bookingId": 1234,
           "caseNoteType": "CHAP",
           "caseNoteSubType": "MAIL ROOM",
           "caseNoteId": -12345,
           "eventDatetime": "${LocalDateTime.parse("2022-12-04T10:00:00")}" 
        } 
        """.trimIndent(),
      )
      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient, times(1)).publish(capture())
        payload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient).trackEvent(any(), capture(), isNull())
        telemetryAttributes = firstValue
      }
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
        "OFFENDER_CASE_NOTES-DELETED",
        """
        {
           "bookingId": 1234,
           "caseNoteType": "CHAP",
           "caseNoteSubType": "MAIL ROOM",
           "caseNoteId": -12345,
           "eventDatetime": "${LocalDateTime.parse("2022-12-04T10:00:00")}" 
        } 
        """.trimIndent(),
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

  @Nested
  internal inner class PrisonerCellMove {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      emitter.convertAndSendWhenSignificant(
        "BED_ASSIGNMENT_HISTORY-INSERTED",
        """
        {
           "offenderIdDisplay": "A1234GH",
           "bookingId": 1234,
           "bedAssignmentSeq": 1,
           "livingUnitId": 4012,
           "eventDatetime": "${LocalDateTime.parse("2022-12-04T10:00:00")}" 
        } 
        """.trimIndent(),
      )
      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient).publish(capture())
        payload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient).trackEvent(
          any(),
          capture(),
          isNull(),
        )
        telemetryAttributes = firstValue
      }
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
    @DisplayName("additional information will contain the  bed assignment and living id")
    fun additionalInformationWillContainCaseNoteType() {
      assertThatJson(payload).node("additionalInformation.bedAssignmentSeq").isEqualTo("\"1\"")
      assertThatJson(payload).node("additionalInformation.livingUnitId").isEqualTo("\"4012\"")
      assertThatJson(payload).node("additionalInformation.bookingId").isEqualTo("\"1234\"")
    }

    @Test
    @DisplayName("will describe the event as a cell move")
    fun willDescribeTheEventAsACaseNote() {
      assertThatJson(payload).node("description")
        .isEqualTo("A prisoner has been moved to a different cell")
    }

    @Test
    @DisplayName("will add correct fields to telemetry event")
    fun willAddNomsNumberToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2022-12-04T10:00:00Z")
      assertThat(telemetryAttributes).containsEntry("bedAssignmentSeq", "1")
      assertThat(telemetryAttributes).containsEntry("livingUnitId", "4012")
    }

    @Test
    @DisplayName("will contain no other telemetry properties")
    fun willContainNoOtherTelemetryProperties() {
      assertThat(telemetryAttributes).containsOnlyKeys(
        "eventType",
        "nomsNumber",
        "occurredAt",
        "publishedAt",
        "bedAssignmentSeq",
        "livingUnitId",
        "bookingId",
      )
    }
  }

  @Nested
  internal inner class NonAssociationDetail {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      emitter.convertAndSendWhenSignificant(
        "NON_ASSOCIATION_DETAIL-UPSERTED",
        """
        {
           "offenderIdDisplay": "A1234GH",
           "bookingId": 1234,
           "nsOffenderIdDisplay": "G5678HJ",
           "nsBookingId": 5678,
           "reasonCode": "REASON",
           "levelCode": "LEVEL",
           "nsType": "WING",
           "typeSeq": 3,
           "effectiveDate": "${LocalDate.parse("2022-12-04")}",
           "expiryDate": "${LocalDate.parse("2022-12-05")}",
           "authorisedBy": "me",
           "comment": "a test",
           "eventDatetime": "${LocalDateTime.parse("2022-12-04T10:00:00")}"
        } 
        """.trimIndent(),
      )
      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient).publish(capture())
        payload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient).trackEvent(
          any(),
          capture(),
          isNull(),
        )
        telemetryAttributes = firstValue
      }
    }

    @Test
    fun `will use event datetime for occurred at time`() {
      assertThatJson(payload).node("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
    }

    @Test
    fun `will user current time as publishedAt`() {
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
    fun `person reference will contain offenderNumber as NOMS number`() {
      assertThatJson(payload).node("personReference.identifiers").isArray.hasSize(1)
      assertThatJson(payload).node("personReference.identifiers[0].type").isEqualTo("NOMS")
      assertThatJson(payload).node("personReference.identifiers[0].value").isEqualTo("A1234GH")
    }

    @Test
    fun `additional information will contain the correct fields`() {
      assertThatJson(payload).node("additionalInformation.bookingId").isEqualTo("\"1234\"")
      assertThatJson(payload).node("additionalInformation.nonAssociationNomsNumber").isEqualTo("\"G5678HJ\"")
      assertThatJson(payload).node("additionalInformation.nonAssociationBookingId").isEqualTo("\"5678\"")
      assertThatJson(payload).node("additionalInformation.reasonCode").isEqualTo("\"REASON\"")
      assertThatJson(payload).node("additionalInformation.levelCode").isEqualTo("\"LEVEL\"")
      assertThatJson(payload).node("additionalInformation.nonAssociationType").isEqualTo("\"WING\"")
      assertThatJson(payload).node("additionalInformation.typeSeq").isEqualTo("\"3\"")
      assertThatJson(payload).node("additionalInformation.effectiveDate").isEqualTo("\"2022-12-04\"")
      assertThatJson(payload).node("additionalInformation.expiryDate").isEqualTo("\"2022-12-05\"")
      assertThatJson(payload).node("additionalInformation.authorisedBy").isEqualTo("\"me\"")
      assertThatJson(payload).node("additionalInformation.comment").isEqualTo("\"a test\"")
    }

    @Test
    fun `will describe the event as a non-association`() {
      assertThatJson(payload).node("description")
        .isEqualTo("A prisoner non-association detail record has changed")
    }

    @Test
    fun `will add correct fields to telemetry event`() {
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2022-12-04T10:00:00Z")
      assertThat(telemetryAttributes).containsEntry("nonAssociationNomsNumber", "G5678HJ")
      assertThat(telemetryAttributes).containsEntry("reasonCode", "REASON")
    }

    @Test
    fun `will contain no other telemetry properties`() {
      assertThat(telemetryAttributes).containsOnlyKeys(
        "eventType",
        "nomsNumber",
        "occurredAt",
        "publishedAt",
        "nonAssociationNomsNumber",
        "bookingId",
        "nonAssociationBookingId",
        "reasonCode",
        "levelCode",
        "nonAssociationType",
        "typeSeq",
        "effectiveDate",
        "expiryDate",
        "authorisedBy",
        "comment",
      )
    }
  }

  @Nested
  internal inner class Restriction {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      emitter.convertAndSendWhenSignificant(
        "RESTRICTION-UPSERTED",
        """
        {
           "offenderIdDisplay": "A1234GH",
           "bookingId": 1234,
           "offenderRestrictionId": 1,
           "restrictionType": "SEC",
           "effectiveDate": "${LocalDate.parse("2022-12-04")}",
           "expiryDate": "${LocalDate.parse("2022-12-05")}",
           "comment": "a test",
           "authorisedById": 2,
           "enteredById": 3,
           "eventDatetime": "${LocalDateTime.parse("2022-12-04T10:00:00")}"
        } 
        """.trimIndent(),
      )
      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient).publish(capture())
        payload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient).trackEvent(
          any(),
          capture(),
          isNull(),
        )
        telemetryAttributes = firstValue
      }
    }

    @Test
    fun `will use event datetime for occurred at time`() {
      assertThatJson(payload).node("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
    }

    @Test
    fun `will user current time as publishedAt`() {
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
    fun `person reference will contain offenderNumber as NOMS number`() {
      assertThatJson(payload).node("personReference.identifiers").isArray.hasSize(1)
      assertThatJson(payload).node("personReference.identifiers[0].type").isEqualTo("NOMS")
      assertThatJson(payload).node("personReference.identifiers[0].value").isEqualTo("A1234GH")
    }

    @Test
    fun `additional information will contain the correct fields`() {
      assertThatJson(payload).node("additionalInformation.bookingId").isEqualTo("\"1234\"")
      assertThatJson(payload).node("additionalInformation.offenderRestrictionId").isEqualTo("\"1\"")
      assertThatJson(payload).node("additionalInformation.restrictionType").isEqualTo("\"SEC\"")
      assertThatJson(payload).node("additionalInformation.effectiveDate").isEqualTo("\"2022-12-04\"")
      assertThatJson(payload).node("additionalInformation.expiryDate").isEqualTo("\"2022-12-05\"")
      assertThatJson(payload).node("additionalInformation.comment").isEqualTo("\"a test\"")
      assertThatJson(payload).node("additionalInformation.authorisedById").isEqualTo("\"2\"")
      assertThatJson(payload).node("additionalInformation.enteredById").isEqualTo("\"3\"")
    }

    @Test
    fun `will describe the event as a restriction`() {
      assertThatJson(payload).node("description")
        .isEqualTo("A prisoner restriction record has changed")
    }

    @Test
    fun `will add correct fields to telemetry event`() {
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2022-12-04T10:00:00Z")
      assertThat(telemetryAttributes).containsEntry("restrictionType", "SEC")
      assertThat(telemetryAttributes).containsEntry("comment", "a test")
    }

    @Test
    fun `will contain no other telemetry properties`() {
      assertThat(telemetryAttributes).containsOnlyKeys(
        "eventType",
        "nomsNumber",
        "occurredAt",
        "publishedAt",
        "bookingId",
        "restrictionType",
        "offenderRestrictionId",
        "effectiveDate",
        "expiryDate",
        "comment",
        "authorisedById",
        "enteredById",
      )
    }
  }

  @Nested
  internal inner class PersonRestriction {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      emitter.convertAndSendWhenSignificant(
        "PERSON_RESTRICTION-UPSERTED",
        """
        {
           "offenderIdDisplay": "A1234GH",
           "offenderPersonRestrictionId": 1,
           "restrictionType": "SEC",
           "effectiveDate": "${LocalDate.parse("2022-12-04")}",
           "expiryDate": "${LocalDate.parse("2022-12-05")}",
           "authorisedById": 2,
           "enteredById": 3,
           "comment": "a test",
           "eventDatetime": "${LocalDateTime.parse("2022-12-04T10:00:00")}"
        } 
        """.trimIndent(),
      )
      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient).publish(capture())
        payload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient).trackEvent(
          any(),
          capture(),
          isNull(),
        )
        telemetryAttributes = firstValue
      }
    }

    @Test
    fun `will use event datetime for occurred at time`() {
      assertThatJson(payload).node("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
    }

    @Test
    fun `will user current time as publishedAt`() {
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
    fun `person reference will contain offenderNumber as NOMS number`() {
      assertThatJson(payload).node("personReference.identifiers").isArray.hasSize(1)
      assertThatJson(payload).node("personReference.identifiers[0].type").isEqualTo("NOMS")
      assertThatJson(payload).node("personReference.identifiers[0].value").isEqualTo("A1234GH")
    }

    @Test
    fun `additional information will contain the correct fields`() {
      assertThatJson(payload).node("additionalInformation.offenderPersonRestrictionId").isEqualTo("\"1\"")
      assertThatJson(payload).node("additionalInformation.restrictionType").isEqualTo("\"SEC\"")
      assertThatJson(payload).node("additionalInformation.effectiveDate").isEqualTo("\"2022-12-04\"")
    }

    @Test
    fun `will describe the event as a restriction`() {
      assertThatJson(payload).node("description")
        .isEqualTo("A prisoner person restriction record has changed")
    }

    @Test
    fun `will add correct fields to telemetry event`() {
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2022-12-04T10:00:00Z")
      assertThat(telemetryAttributes).containsEntry("restrictionType", "SEC")
      assertThat(telemetryAttributes).containsEntry("comment", "a test")
    }

    @Test
    fun `will contain no other telemetry properties`() {
      assertThat(telemetryAttributes).containsOnlyKeys(
        "eventType",
        "nomsNumber",
        "occurredAt",
        "publishedAt",
        "offenderPersonRestrictionId",
        "restrictionType",
        "effectiveDate",
        "expiryDate",
        "authorisedById",
        "enteredById",
        "comment",
      )
    }
  }

  @Nested
  internal inner class VisitorRestriction {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      emitter.convertAndSendWhenSignificant(
        "VISITOR_RESTRICTION-UPSERTED",
        """
        {
           "personId": 1,
           "restrictionType": "SEC",
           "effectiveDate": "${LocalDate.parse("2022-12-04")}",
           "expiryDate": "${LocalDate.parse("2022-12-05")}",
           "comment": "a test",
           "visitorRestrictionId": 2,
           "enteredById": 3,
           "eventDatetime": "${LocalDateTime.parse("2022-12-04T10:00:00")}"
        } 
        """.trimIndent(),
      )
      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient).publish(capture())
        payload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient).trackEvent(
          any(),
          capture(),
          isNull(),
        )
        telemetryAttributes = firstValue
      }
    }

    @Test
    fun `will use event datetime for occurred at time`() {
      assertThatJson(payload).node("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
    }

    @Test
    fun `will user current time as publishedAt`() {
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
    fun `person reference will contain personId as PERSON identifier`() {
      assertThatJson(payload).node("personReference.identifiers").isArray.hasSize(1)
      assertThatJson(payload).node("personReference.identifiers[0].type").isEqualTo("PERSON")
      assertThatJson(payload).node("personReference.identifiers[0].value").isEqualTo("\"1\"")
    }

    @Test
    fun `additional information will contain the correct fields`() {
      assertThatJson(payload).node("additionalInformation.personId").isEqualTo("\"1\"")
      assertThatJson(payload).node("additionalInformation.restrictionType").isEqualTo("\"SEC\"")
      assertThatJson(payload).node("additionalInformation.visitorRestrictionId").isEqualTo("\"2\"")
      assertThatJson(payload).node("additionalInformation.effectiveDate").isEqualTo("\"2022-12-04\"")
    }

    @Test
    fun `will describe the event as a restriction`() {
      assertThatJson(payload).node("description")
        .isEqualTo("A prisoner visitor restriction record has changed")
    }

    @Test
    fun `will add correct fields to telemetry event`() {
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2022-12-04T10:00:00Z")
      assertThat(telemetryAttributes).containsEntry("restrictionType", "SEC")
      assertThat(telemetryAttributes).containsEntry("comment", "a test")
    }

    @Test
    fun `will contain no other telemetry properties`() {
      assertThat(telemetryAttributes).containsOnlyKeys(
        "eventType",
        "occurredAt",
        "publishedAt",
        "personId",
        "restrictionType",
        "effectiveDate",
        "expiryDate",
        "comment",
        "visitorRestrictionId",
        "enteredById",
      )
    }
  }

  @Nested
  internal inner class PrisonerActivityUpdated {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      emitter.convertAndSendWhenSignificant(
        "PRISONER_ACTIVITY-UPDATED",
        """
        {
           "offenderIdDisplay": "A1234BC",
           "prisonId": "LEI",
           "staffId": "Some staff ID",
           "suspendActivities": "true",
           "endActivities": "false",
           "eventDatetime": "${LocalDateTime.parse("2022-12-04T10:00:00")}"
        } 
        """.trimIndent(),
      )
      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient).publish(capture())
        payload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient).trackEvent(
          any(),
          capture(),
          isNull(),
        )
        telemetryAttributes = firstValue
      }
    }

    @Test
    fun `will use event datetime for occurred at time`() {
      assertThatJson(payload).node("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
    }

    @Test
    fun `will use current time as publishedAt`() {
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
    fun `person reference will contain nomsId as NOMS identifier`() {
      assertThatJson(payload).node("personReference.identifiers").isArray.hasSize(1)
      assertThatJson(payload).node("personReference.identifiers[0].type").isEqualTo("NOMS")
      assertThatJson(payload).node("personReference.identifiers[0].value").isEqualTo("\"A1234BC\"")
    }

    @Test
    fun `additional information will contain the correct fields`() {
      assertThatJson(payload).node("additionalInformation.prisonId").isEqualTo("\"LEI\"")
      assertThatJson(payload).node("additionalInformation.staffId").isEqualTo("\"Some Staff ID\"")
      assertThatJson(payload).node("additionalInformation.supsendActivities").isEqualTo("\"true\"")
      assertThatJson(payload).node("additionalInformation.endActivities").isEqualTo("\"false\"")
    }

    @Test
    fun `will describe the event as a prisoner activity update`() {
      assertThatJson(payload).node("description")
        .isEqualTo("A prisoner's activities have been changed")
    }

    @Test
    fun `will add correct fields to telemetry event`() {
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2022-12-04T10:00:00Z")
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234BC")
      assertThat(telemetryAttributes).containsEntry("suspendActivities", "true")
      assertThat(telemetryAttributes).containsEntry("endActivities", "false")
    }

    @Test
    fun `will contain no other telemetry properties`() {
      assertThat(telemetryAttributes).containsOnlyKeys(
        "eventType",
        "occurredAt",
        "publishedAt",
        "nomsNumber",
        "prisonId",
        "staffId",
        "suspendActivities",
        "endActivities",
      )
    }
  }

  companion object {
    @JvmStatic
    private fun eventMap() = listOf(
      Arguments.of("OFFENDER_MOVEMENT-DISCHARGE", "prison-offender-events.prisoner.released"),
      Arguments.of("OFFENDER_MOVEMENT-RECEPTION", "prison-offender-events.prisoner.received"),
    )

    @JvmStatic
    private fun bookingChangedEventMap() = listOf(
      Arguments.of("BOOKING_NUMBER-CHANGED", "prison-offender-events.prisoner.merged"),
    )
  }
}
