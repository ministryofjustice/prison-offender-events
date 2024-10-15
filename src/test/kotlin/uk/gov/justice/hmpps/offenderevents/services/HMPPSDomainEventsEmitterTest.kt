package uk.gov.justice.hmpps.offenderevents.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import software.amazon.awssdk.services.sns.model.PublishResponse
import uk.gov.justice.hmpps.offenderevents.config.OffenderEventsProperties
import uk.gov.justice.hmpps.offenderevents.helpers.assertJsonPath
import uk.gov.justice.hmpps.offenderevents.helpers.assertJsonPathDateTimeIsCloseTo
import uk.gov.justice.hmpps.offenderevents.helpers.assertJsonPathIsArray
import uk.gov.justice.hmpps.offenderevents.services.CurrentLocation.IN_PRISON
import uk.gov.justice.hmpps.offenderevents.services.CurrentLocation.OUTSIDE_PRISON
import uk.gov.justice.hmpps.offenderevents.services.CurrentPrisonStatus.NOT_UNDER_PRISON_CARE
import uk.gov.justice.hmpps.offenderevents.services.CurrentPrisonStatus.UNDER_PRISON_CARE
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.ReceiveReason
import uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.MovementReason
import uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.Reason
import uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.Reason.TEMPORARY_ABSENCE_RELEASE
import uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.ReleaseReason
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit.SECONDS
import java.util.Optional
import java.util.concurrent.CompletableFuture

@JsonTest
internal class HMPPSDomainEventsEmitterTest(@Autowired private val objectMapper: ObjectMapper) {
  private lateinit var emitter: HMPPSDomainEventsEmitter

  private val receivePrisonerReasonCalculator: ReceivePrisonerReasonCalculator = mock()
  private val releasePrisonerReasonCalculator: ReleasePrisonerReasonCalculator = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val offenderEventsProperties: OffenderEventsProperties = mock()
  private val prisonApiService: PrisonApiService = mock()

  private val hmppsQueueService: HmppsQueueService = mock()
  private val hmppsEventSnsClient: SnsAsyncClient = mock()

  @BeforeEach
  fun setUp() {
    whenever(hmppsQueueService.findByTopicId("hmppseventtopic"))
      .thenReturn(HmppsTopic("hmppseventtopic", "sometopicarn", hmppsEventSnsClient))

    whenever(hmppsEventSnsClient.publish(any<PublishRequest>())).thenReturn(CompletableFuture.completedFuture(PublishResponse.builder().build()))
    emitter = HMPPSDomainEventsEmitter(
      hmppsQueueService,
      objectMapper,
      receivePrisonerReasonCalculator,
      releasePrisonerReasonCalculator,
      telemetryClient,
      offenderEventsProperties,
      prisonApiService,
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
      payload.assertJsonPath("eventType").isEqualTo(eventType)
      payload.assertJsonPath("version").isEqualTo(1)
      assertThat(
        messageAttributes["eventType"],
      )
        .isEqualTo(MessageAttributeValue.builder().stringValue(eventType).dataType("String").build())
      verify(telemetryClient).trackEvent(ArgumentMatchers.eq(eventType), ArgumentMatchers.anyMap(), isNull())
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
            "some details",
            IN_PRISON,
            UNDER_PRISON_CARE,
            "MDI",
            ReceivePrisonerReasonCalculator.MovementReason("N"),
          ),
        )

      emitter.convertAndSendWhenSignificant(
        "OFFENDER_MOVEMENT-RECEPTION",
        // language=JSON
        """
        {
           "offenderIdDisplay": "A1234GH",
           "eventDatetime": "2020-12-04T10:42:43" 
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
      payload.assertJsonPath("occurredAt", "2020-12-04T10:42:43Z")
    }

    @Test
    @DisplayName("will user current time as publishedAt")
    fun willUserCurrentTimeAsPublishedAt() {
      payload.assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, SECONDS))
    }

    @Test
    @DisplayName("additionalInformation will contain offenderNumber as NOMS number")
    fun additionalInformationWillContainOffenderNumberAsNOMSNumber() {
      payload.assertJsonPath("additionalInformation.nomsNumber", "A1234GH")
    }

    @Test
    @DisplayName("will describe the prisoners last (or current) location")
    fun additionalInformationWillContainPrisonId() {
      payload.assertJsonPath("additionalInformation.prisonId", "MDI")
    }

    @Test
    @DisplayName("will indicate the reason for a prisoners entry")
    fun willIndicateTheReasonForAPrisonersEntry() {
      payload.assertJsonPath("additionalInformation.reason", "ADMISSION")
    }

    @Test
    @DisplayName("will describe the event as a receive")
    fun willDescribeTheEventAsAReceive() {
      payload.assertJsonPath("description")
        .isEqualTo("A prisoner has been received into prison")
    }

    @Test
    @DisplayName("will pass through the nomis movement reason code")
    fun willPassThroughNOMISReasonCode() {
      payload.assertJsonPath("additionalInformation.nomisMovementReasonCode")
        .isEqualTo("N")
    }

    @Test
    @DisplayName("will describe the prisoners current location and status")
    fun willDescribeThePrisonersCurrentLocation() {
      payload.assertJsonPath("additionalInformation.currentLocation").isEqualTo("IN_PRISON")
      payload.assertJsonPath("additionalInformation.currentPrisonStatus")
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
    @DisplayName("will add occurredAt to telemetry event")
    fun willAddOccurredAtToTelemetryEvent() {
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2020-12-04T10:42:43Z")
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
            "some details",
            OUTSIDE_PRISON,
            NOT_UNDER_PRISON_CARE,
            "MDI",
            ReceivePrisonerReasonCalculator.MovementReason("N"),
          ),
        )

      emitter.convertAndSendWhenSignificant(
        "OFFENDER_MOVEMENT-RECEPTION",
        // language=JSON
        """
        {
           "offenderIdDisplay": "A1234GH",
           "eventDatetime": "2020-12-04T10:42:43" 
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
        // language=JSON
        """
        {
           "offenderIdDisplay": "A1234GH",
           "eventDatetime": "2020-07-04T10:42:43" 
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
      payload.assertJsonPath("occurredAt").isEqualTo("2020-07-04T10:42:43+01:00")
    }

    @Test
    @DisplayName("will user current time as publishedAt")
    fun willUserCurrentTimeAsPublishedAt() {
      payload.assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, SECONDS))
    }

    @Test
    @DisplayName("additionalInformation will contain offenderNumber as NOMS number")
    fun additionalInformationWillContainOffenderNumberAsNOMSNumber() {
      payload.assertJsonPath("additionalInformation.nomsNumber").isEqualTo("A1234GH")
    }

    @Test
    @DisplayName("will describe the prisoners last (or current) location")
    fun additionalInformationWillContainPrisonId() {
      payload.assertJsonPath("additionalInformation.prisonId").isEqualTo("MDI")
    }

    @Test
    @DisplayName("will indicate the reason for a prisoners exit")
    fun willIndicateTheReasonForAPrisonersExit() {
      payload.assertJsonPath("additionalInformation.reason")
        .isEqualTo("TEMPORARY_ABSENCE_RELEASE")
    }

    @Test
    @DisplayName("will pass through the nomis movement reason code")
    fun willPassThroughNOMISReasonCode() {
      payload.assertJsonPath("additionalInformation.nomisMovementReasonCode")
        .isEqualTo("N")
    }

    @Test
    @DisplayName("will describe the event as a release")
    fun willDescribeTheEventAsAReceive() {
      payload.assertJsonPath("description")
        .isEqualTo("A prisoner has been released from prison")
    }

    @Test
    @DisplayName("will describe the prisoners current location and status")
    fun willDescribeThePrisonersCurrentLocation() {
      payload.assertJsonPath("additionalInformation.currentLocation")
        .isEqualTo("OUTSIDE_PRISON")
      payload.assertJsonPath("additionalInformation.currentPrisonStatus")
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
        // language=JSON
        """
        {
           "offenderIdDisplay": "A1234GH",
           "eventDatetime": "2020-12-04T10:42:43" 
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
  internal inner class CaseNotePublished {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      whenever(offenderEventsProperties.casenotesApiBaseUrl).thenReturn("http://localhost:1234")
      emitter.convertAndSendWhenSignificant(
        "OFFENDER_CASE_NOTES-INSERTED",
        // language=JSON
        """
        {
           "offenderIdDisplay": "A1234GH",
           "bookingId": 1234,
           "caseNoteType": "CHAP",
           "caseNoteSubType": "MAIL ROOM",
           "caseNoteId": -12345,
           "eventDatetime": "2022-12-04T10:00:00" 
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
      payload.assertJsonPath("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
    }

    @Test
    @DisplayName("will user current time as publishedAt")
    fun willUseCurrentTimeAsPublishedAt() {
      payload.assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, SECONDS))
    }

    @Test
    @DisplayName("person reference will contain offenderNumber as NOMS number")
    fun personReferenceWillContainOffenderNumberAsNOMSNumber() {
      payload.assertJsonPathIsArray("personReference.identifiers").hasSize(1)
      payload.assertJsonPath("personReference.identifiers[0].type").isEqualTo("NOMS")
      payload.assertJsonPath("personReference.identifiers[0].value").isEqualTo("A1234GH")
    }

    @Test
    @DisplayName("additional information will contain the case note type, id, raw type and subtype")
    fun additionalInformationWillContainCaseNoteType() {
      payload.assertJsonPath("additionalInformation.caseNoteType").isEqualTo("CHAP-MAIL")
      payload.assertJsonPath("additionalInformation.caseNoteId").isEqualTo("-12345")
      payload.assertJsonPath("additionalInformation.type").isEqualTo("CHAP")
      payload.assertJsonPath("additionalInformation.subType").isEqualTo("MAIL ROOM")
    }

    @Test
    @DisplayName("detail url will be set to the offender-case-notes endpoint")
    fun detailUrlWillBeSetToCaseNotesService() {
      payload.assertJsonPath("detailUrl")
        .isEqualTo("http://localhost:1234/case-notes/A1234GH/-12345")
    }

    @Test
    @DisplayName("will describe the event as a case note")
    fun willDescribeTheEventAsACaseNote() {
      payload.assertJsonPath("description")
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
        //language=JSON
        """
        {
           "bookingId": 1234,
           "caseNoteType": "CHAP",
           "caseNoteSubType": "MAIL ROOM",
           "caseNoteId": -12345,
           "eventDatetime": "2022-12-04T10:00:00" 
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
        //language=JSON
        """
        {
           "offenderIdDisplay": "A1234GH",
           "bookingId": 1234,
           "bedAssignmentSeq": 1,
           "livingUnitId": 4012,
           "eventDatetime": "2022-12-04T10:00:00" 
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
      payload.assertJsonPath("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
    }

    @Test
    @DisplayName("will user current time as publishedAt")
    fun willUseCurrentTimeAsPublishedAt() {
      payload.assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, SECONDS))
    }

    @Test
    @DisplayName("person reference will contain offenderNumber as NOMS number")
    fun personReferenceWillContainOffenderNumberAsNOMSNumber() {
      payload.assertJsonPathIsArray("personReference.identifiers").hasSize(1)
      payload.assertJsonPath("personReference.identifiers[0].type").isEqualTo("NOMS")
      payload.assertJsonPath("personReference.identifiers[0].value").isEqualTo("A1234GH")
    }

    @Test
    @DisplayName("additional information will contain the  bed assignment and living id")
    fun additionalInformationWillContainCaseNoteType() {
      payload.assertJsonPath("additionalInformation.bedAssignmentSeq").isEqualTo("1")
      payload.assertJsonPath("additionalInformation.livingUnitId").isEqualTo("4012")
      payload.assertJsonPath("additionalInformation.bookingId").isEqualTo("1234")
    }

    @Test
    @DisplayName("will describe the event as a cell move")
    fun willDescribeTheEventAsACaseNote() {
      payload.assertJsonPath("description")
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
        //language=JSON
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
           "effectiveDate": "2022-12-04",
           "expiryDate": "2022-12-05",
           "authorisedBy": "me",
           "comment": "a test",
           "eventDatetime": "2022-12-04T10:00:00"
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
      payload.assertJsonPath("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
    }

    @Test
    fun `will user current time as publishedAt`() {
      payload.assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, SECONDS))
    }

    @Test
    fun `person reference will contain offenderNumber as NOMS number`() {
      payload.assertJsonPathIsArray("personReference.identifiers").hasSize(1)
      payload.assertJsonPath("personReference.identifiers[0].type").isEqualTo("NOMS")
      payload.assertJsonPath("personReference.identifiers[0].value").isEqualTo("A1234GH")
    }

    @Test
    fun `additional information will contain the correct fields`() {
      payload.assertJsonPath("additionalInformation.bookingId").isEqualTo("1234")
      payload.assertJsonPath("additionalInformation.nonAssociationNomsNumber").isEqualTo("G5678HJ")
      payload.assertJsonPath("additionalInformation.nonAssociationBookingId").isEqualTo("5678")
      payload.assertJsonPath("additionalInformation.reasonCode").isEqualTo("REASON")
      payload.assertJsonPath("additionalInformation.levelCode").isEqualTo("LEVEL")
      payload.assertJsonPath("additionalInformation.nonAssociationType").isEqualTo("WING")
      payload.assertJsonPath("additionalInformation.typeSeq").isEqualTo("3")
      payload.assertJsonPath("additionalInformation.effectiveDate").isEqualTo("2022-12-04")
      payload.assertJsonPath("additionalInformation.expiryDate").isEqualTo("2022-12-05")
      payload.assertJsonPath("additionalInformation.authorisedBy").isEqualTo("me")
      payload.assertJsonPath("additionalInformation.comment").isEqualTo("a test")
    }

    @Test
    fun `will describe the event as a non-association`() {
      payload.assertJsonPath("description")
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
        //language=JSON
        """
        {
           "offenderIdDisplay": "A1234GH",
           "bookingId": 1234,
           "offenderRestrictionId": 1,
           "restrictionType": "SEC",
           "effectiveDate": "2022-12-04",
           "expiryDate": "2022-12-05",
           "authorisedById": 2,
           "enteredById": 3,
           "eventDatetime": "2022-12-04T10:00:00"
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
      payload.assertJsonPath("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
    }

    @Test
    fun `will user current time as publishedAt`() {
      payload.assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, SECONDS))
    }

    @Test
    fun `person reference will contain offenderNumber as NOMS number`() {
      payload.assertJsonPathIsArray("personReference.identifiers").hasSize(1)
      payload.assertJsonPath("personReference.identifiers[0].type").isEqualTo("NOMS")
      payload.assertJsonPath("personReference.identifiers[0].value").isEqualTo("A1234GH")
    }

    @Test
    fun `additional information will contain the correct fields`() {
      payload.assertJsonPath("additionalInformation.bookingId").isEqualTo("1234")
      payload.assertJsonPath("additionalInformation.offenderRestrictionId").isEqualTo("1")
      payload.assertJsonPath("additionalInformation.restrictionType").isEqualTo("SEC")
      payload.assertJsonPath("additionalInformation.effectiveDate").isEqualTo("2022-12-04")
      payload.assertJsonPath("additionalInformation.expiryDate").isEqualTo("2022-12-05")
      payload.assertJsonPath("additionalInformation.authorisedById").isEqualTo("2")
      payload.assertJsonPath("additionalInformation.enteredById").isEqualTo("3")
    }

    @Test
    fun `will describe the event as a restriction`() {
      payload.assertJsonPath("description")
        .isEqualTo("A prisoner restriction record has changed")
    }

    @Test
    fun `will add correct fields to telemetry event`() {
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2022-12-04T10:00:00Z")
      assertThat(telemetryAttributes).containsEntry("restrictionType", "SEC")
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
        "authorisedById",
        "enteredById",
      )
    }
  }

  @Nested
  internal inner class PersonRestrictionUpserted {
    private lateinit var payload: String
    private lateinit var telemetryAttributes: Map<String, String>

    @BeforeEach
    fun setUp() {
      emitter.convertAndSendWhenSignificant(
        "PERSON_RESTRICTION-UPSERTED",
        """
        {
           "offenderIdDisplay": "A1234GH",
           "offenderPersonRestrictionId": 1,
           "personId": 4,
           "restrictionType": "SEC",
           "effectiveDate": "2022-12-04",
           "expiryDate": "2022-12-05",
           "authorisedById": 2,
           "enteredById": 3,
           "eventDatetime": "2022-12-04T10:00:00"
        } 
        """.trimIndent(),
      )
      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient).publish(capture())
        payload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient).trackEvent(any(), capture(), isNull())
        telemetryAttributes = firstValue
      }
    }

    @Test
    fun `will have the correct basic fields`() {
      payload.assertJsonPath("eventType")
        .isEqualTo("prison-offender-events.prisoner.person-restriction.upserted")
      payload.assertJsonPath("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
      payload.assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, SECONDS))
    }

    @Test
    fun `person reference will contain offenderNumber as NOMS number`() {
      payload.assertJsonPathIsArray("personReference.identifiers") // .isArray.hasSize(1)
      payload.assertJsonPath("personReference.identifiers[0].type").isEqualTo("NOMS")
      payload.assertJsonPath("personReference.identifiers[0].value").isEqualTo("A1234GH")
    }

    @Test
    fun `additional information will contain the correct fields`() {
      payload.assertJsonPath("additionalInformation.offenderPersonRestrictionId").isEqualTo("1")
      payload.assertJsonPath("additionalInformation.restrictionType").isEqualTo("SEC")
      payload.assertJsonPath("additionalInformation.effectiveDate").isEqualTo("2022-12-04")
      payload.assertJsonPath("additionalInformation.personId").isEqualTo("4")
    }

    @Test
    fun `will describe the event as a restriction`() {
      payload.assertJsonPath("description")
        .isEqualTo("A prisoner person restriction record has been created or updated")
    }

    @Test
    fun `will add correct fields to telemetry event`() {
      assertThat(telemetryAttributes).containsEntry(
        "eventType",
        "prison-offender-events.prisoner.person-restriction.upserted",
      )
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2022-12-04T10:00:00Z")
      assertThat(telemetryAttributes).containsEntry("restrictionType", "SEC")
    }

    @Test
    fun `will contain no other telemetry properties`() {
      val keys = listOf(
        "eventType",
        "nomsNumber",
        "occurredAt",
        "publishedAt",
        "offenderPersonRestrictionId",
        "personId",
        "restrictionType",
        "effectiveDate",
        "expiryDate",
        "authorisedById",
        "enteredById",
      )
      assertThat(telemetryAttributes).containsOnlyKeys(keys)
    }
  }

  @Nested
  internal inner class PersonRestrictionDeleted {
    private lateinit var payload: String
    private lateinit var telemetryAttributes: Map<String, String>

    @BeforeEach
    fun setUp() {
      emitter.convertAndSendWhenSignificant(
        "PERSON_RESTRICTION-DELETED",
        //language=JSON
        """
        {
           "offenderIdDisplay": "A1234GH",
           "offenderPersonRestrictionId": 1,
           "restrictionType": "SEC",
           "effectiveDate": "2022-12-04",
           "expiryDate": "2022-12-05",
           "authorisedById": 2,
           "enteredById": 3,
           "eventDatetime": "2022-12-04T10:00:00"
        } 
        """.trimIndent(),
      )
      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient).publish(capture())
        payload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient).trackEvent(any(), capture(), isNull())
        telemetryAttributes = firstValue
      }
    }

    @Test
    fun `will have the correct basic fields`() {
      payload.assertJsonPath("eventType").isEqualTo("prison-offender-events.prisoner.person-restriction.deleted")
      payload.assertJsonPath("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
      payload.assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, SECONDS))
    }

    @Test
    fun `person reference will contain offenderNumber as NOMS number`() {
      payload.assertJsonPathIsArray("personReference.identifiers").hasSize(1)
      payload.assertJsonPath("personReference.identifiers[0].type").isEqualTo("NOMS")
      payload.assertJsonPath("personReference.identifiers[0].value").isEqualTo("A1234GH")
    }

    @Test
    fun `additional information will contain the correct fields`() {
      payload.assertJsonPath("additionalInformation.offenderPersonRestrictionId").isEqualTo("1")
      payload.assertJsonPath("additionalInformation.restrictionType").isEqualTo("SEC")
      payload.assertJsonPath("additionalInformation.effectiveDate").isEqualTo("2022-12-04")
    }

    @Test
    fun `will describe the event as a restriction`() {
      payload.assertJsonPath("description").isEqualTo("A prisoner person restriction record has been deleted")
    }

    @Test
    fun `will add correct fields to telemetry event`() {
      assertThat(telemetryAttributes).containsEntry("eventType", "prison-offender-events.prisoner.person-restriction.deleted")
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2022-12-04T10:00:00Z")
      assertThat(telemetryAttributes).containsEntry("restrictionType", "SEC")
    }

    @Test
    fun `will contain no other telemetry properties`() {
      val keys = listOf(
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
      )
      assertThat(telemetryAttributes).containsOnlyKeys(keys)
    }
  }

  @Nested
  internal inner class VisitorRestrictionUpserted {
    private lateinit var upsertedPayload: String
    private lateinit var upsertedTelemetry: Map<String, String>

    @BeforeEach
    fun setUp() {
      emitter.convertAndSendWhenSignificant(
        "VISITOR_RESTRICTION-UPSERTED",
        // language=JSON
        """
        {
           "nomisEventType": "VISITOR_RESTRICTS-UPDATED",
           "visitorRestrictionId": 1,
           "personId": 4,
           "restrictionType": "DIHCON",
           "effectiveDate": "2022-12-04",
           "expiryDate": "2022-12-05",
           "enteredById": 3,
           "eventDatetime": "2022-12-04T10:00:00"
        } 
        """.trimIndent(),
      )
      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient).publish(capture())
        upsertedPayload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient).trackEvent(any(), capture(), isNull())
        upsertedTelemetry = firstValue
      }
    }

    @DisplayName("will raise prison-offender-events.visitor.restriction.upserted event")
    @Test
    fun willRaiseUpsertedDomainEvent() {
      with(upsertedPayload) {
        assertJsonPath("eventType", "prison-offender-events.visitor.restriction.upserted")
        assertJsonPath("occurredAt", "2022-12-04T10:00:00Z")
        assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, java.time.temporal.ChronoUnit.SECONDS))
        assertJsonPath("personReference.identifiers[0].type", "PERSON")
        assertJsonPath("personReference.identifiers[0].value", "4")
        assertJsonPath("additionalInformation.visitorRestrictionId", "1")
        assertJsonPath("additionalInformation.restrictionType", "DIHCON")
        assertJsonPath("additionalInformation.effectiveDate", "2022-12-04")
        assertJsonPath("additionalInformation.expiryDate", "2022-12-05")
        assertJsonPath("additionalInformation.personId", "4")
      }
    }

    @Test
    fun `will raise telemetry events`() {
      assertThat(upsertedTelemetry).containsEntry("eventType", "prison-offender-events.visitor.restriction.upserted")
    }
  }

  @Nested
  internal inner class VisitorRestrictionDeleted {
    private lateinit var deletedPayload: String
    private lateinit var deletedTelemetry: Map<String, String>

    @BeforeEach
    fun setUp() {
      emitter.convertAndSendWhenSignificant(
        "VISITOR_RESTRICTION-DELETED",
        // language=JSON
        """
        {
           "nomisEventType": "VISITOR_RESTRICTS-UPDATED",
           "visitorRestrictionId": 1,
           "personId": 4,
           "restrictionType": "DIHCON",
           "effectiveDate": "2022-12-04",
           "expiryDate": "2022-12-05",
           "enteredById": 3,
           "eventDatetime": "2022-12-04T10:00:00"
        } 
        """.trimIndent(),
      )
      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient).publish(capture())
        deletedPayload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient).trackEvent(any(), capture(), isNull())
        deletedTelemetry = firstValue
      }
    }

    @DisplayName("will raise prison-offender-events.visitor.restriction.deleted event")
    @Test
    fun willRaiseDeletedDomainEvent() {
      with(deletedPayload) {
        assertJsonPath("eventType", "prison-offender-events.visitor.restriction.deleted")
        assertJsonPath("occurredAt", "2022-12-04T10:00:00Z")
        assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, java.time.temporal.ChronoUnit.SECONDS))
        assertJsonPath("personReference.identifiers[0].type", "PERSON")
        assertJsonPath("personReference.identifiers[0].value", "4")
        assertJsonPath("additionalInformation.visitorRestrictionId", "1")
        assertJsonPath("additionalInformation.restrictionType", "DIHCON")
        assertJsonPath("additionalInformation.effectiveDate", "2022-12-04")
        assertJsonPath("additionalInformation.expiryDate", "2022-12-05")
        assertJsonPath("additionalInformation.personId", "4")
      }
    }

    @Test
    fun `will raise telemetry events`() {
      assertThat(deletedTelemetry).containsEntry("eventType", "prison-offender-events.visitor.restriction.deleted")
    }
  }

  @Nested
  internal inner class PrisonerActivityUpdated {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      emitter.convertAndSendWhenSignificant(
        "PRISONER_ACTIVITY-UPDATE",
        //language=JSON
        """
        {
           "offenderIdDisplay": "A1234BC",
           "prisonId": "LEI",
           "user": "SOME_USER",
           "action": "SUSPEND",
           "eventDatetime": "2022-12-04T10:00:00"
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
      payload.assertJsonPath("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
    }

    @Test
    fun `will use current time as publishedAt`() {
      payload.assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, java.time.temporal.ChronoUnit.SECONDS))
    }

    @Test
    fun `person reference will contain nomsId as NOMS identifier`() {
      payload.assertJsonPathIsArray("personReference.identifiers").hasSize(1)
      payload.assertJsonPath("personReference.identifiers[0].type").isEqualTo("NOMS")
      payload.assertJsonPath("personReference.identifiers[0].value").isEqualTo("A1234BC")
    }

    @Test
    fun `additional information will contain the correct fields`() {
      payload.assertJsonPath("additionalInformation.prisonId").isEqualTo("LEI")
      payload.assertJsonPath("additionalInformation.user").isEqualTo("SOME_USER")
      payload.assertJsonPath("additionalInformation.action").isEqualTo("SUSPEND")
    }

    @Test
    fun `will describe the event as a prisoner activity update`() {
      payload.assertJsonPath("description")
        .isEqualTo("A prisoner's activities have been changed")
    }

    @Test
    fun `will add correct fields to telemetry event`() {
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2022-12-04T10:00:00Z")
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234BC")
      assertThat(telemetryAttributes).containsEntry("action", "SUSPEND")
    }

    @Test
    fun `will contain no other telemetry properties`() {
      assertThat(telemetryAttributes).containsOnlyKeys(
        "eventType",
        "occurredAt",
        "publishedAt",
        "nomsNumber",
        "prisonId",
        "user",
        "action",
      )
    }
  }

  @Nested
  internal inner class PrisonerAppointmentUpdated {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      emitter.convertAndSendWhenSignificant(
        "PRISONER_APPOINTMENT-UPDATE",
        //language=JSON
        """
        {
           "offenderIdDisplay": "A1234BC",
           "prisonId": "LEI",
           "user": "SOME_USER",
           "action": "SUSPEND",
           "eventDatetime": "2022-12-04T10:00:00"
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
      payload.assertJsonPath("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
    }

    @Test
    fun `will use current time as publishedAt`() {
      payload.assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, java.time.temporal.ChronoUnit.SECONDS))
    }

    @Test
    fun `person reference will contain nomsId as NOMS identifier`() {
      payload.assertJsonPathIsArray("personReference.identifiers").hasSize(1)
      payload.assertJsonPath("personReference.identifiers[0].type").isEqualTo("NOMS")
      payload.assertJsonPath("personReference.identifiers[0].value").isEqualTo("A1234BC")
    }

    @Test
    fun `additional information will contain the correct fields`() {
      payload.assertJsonPath("additionalInformation.prisonId").isEqualTo("LEI")
      payload.assertJsonPath("additionalInformation.user").isEqualTo("SOME_USER")
      payload.assertJsonPath("additionalInformation.action").isEqualTo("SUSPEND")
    }

    @Test
    fun `will describe the event as a prisoner activity update`() {
      payload.assertJsonPath("description")
        .isEqualTo("A prisoner's appointment has been changed")
    }

    @Test
    fun `will add correct fields to telemetry event`() {
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2022-12-04T10:00:00Z")
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234BC")
      assertThat(telemetryAttributes).containsEntry("action", "SUSPEND")
    }

    @Test
    fun `will contain no other telemetry properties`() {
      assertThat(telemetryAttributes).containsOnlyKeys(
        "eventType",
        "occurredAt",
        "publishedAt",
        "nomsNumber",
        "prisonId",
        "user",
        "action",
      )
    }
  }

  @Nested
  internal inner class ImprisonmentStatusChanged {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      whenever(prisonApiService.getPrisonerNumberForBookingId(ArgumentMatchers.eq(43124234L)))
        .thenReturn(Optional.of("A1234GH"))

      emitter.convertAndSendWhenSignificant(
        "IMPRISONMENT_STATUS-CHANGED",
        //language=JSON
        """
        {
           "bookingId": 43124234,
           "imprisonmentStatusSeq": 0,
           "eventDatetime": "2022-12-04T10:00:00"
        } 
        """.trimIndent(),
      )
      emitter.convertAndSendWhenSignificant(
        "IMPRISONMENT_STATUS-CHANGED",
        //language=JSON
        """
        {
           "bookingId": 43124234,
           "imprisonmentStatusSeq": 1,
           "eventDatetime": "2022-12-04T10:00:00"
        } 
        """.trimIndent(),
      )
      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient, times(1)).publish(capture())
        payload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient, times(1)).trackEvent(
          any(),
          capture(),
          isNull(),
        )
        telemetryAttributes = firstValue
      }
    }

    @Test
    fun `will use event datetime for occurred at time`() {
      payload.assertJsonPath("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
    }

    @Test
    fun `will use current time as publishedAt`() {
      payload.assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, java.time.temporal.ChronoUnit.SECONDS))
    }

    @Test
    fun `person reference will contain nomsId as NOMS identifier`() {
      payload.assertJsonPathIsArray("personReference.identifiers").hasSize(1)
      payload.assertJsonPath("personReference.identifiers[0].type").isEqualTo("NOMS")
      payload.assertJsonPath("personReference.identifiers[0].value").isEqualTo("A1234GH")
    }

    @Test
    fun `additional information will contain the correct fields`() {
      payload.assertJsonPath("additionalInformation.bookingId").isEqualTo("43124234")
    }

    @Test
    fun `will describe the event as a prisoner activity update`() {
      payload.assertJsonPath("description")
        .isEqualTo("A prisoner's imprisonment status has been changed")
    }

    @Test
    fun `will add correct fields to telemetry event`() {
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2022-12-04T10:00:00Z")
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234GH")
    }

    @Test
    fun `will contain no other telemetry properties`() {
      assertThat(telemetryAttributes).containsOnlyKeys(
        "eventType",
        "occurredAt",
        "publishedAt",
        "nomsNumber",
        "bookingId",
      )
    }
  }

  @Nested
  internal inner class SentenceDatesChanged {
    private var payload: String? = null
    private var telemetryAttributes: Map<String, String>? = null

    @BeforeEach
    fun setUp() {
      emitter.convertAndSendWhenSignificant(
        "SENTENCE_DATES-CHANGED",
        //language=JSON
        """
        {
           "offenderIdDisplay": "A1234BC",
           "bookingId": 43124234,
           "sentenceCalculationId": 0,
           "eventDatetime": "2022-12-04T10:00:00"
        } 
        """.trimIndent(),
      )
      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient).publish(capture())
        payload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient, times(1)).trackEvent(
          any(),
          capture(),
          isNull(),
        )
        telemetryAttributes = firstValue
      }
    }

    @Test
    fun `will use event datetime for occurred at time`() {
      payload.assertJsonPath("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
    }

    @Test
    fun `will use current time as publishedAt`() {
      payload.assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, SECONDS))
    }

    @Test
    fun `person reference will contain nomsId as NOMS identifier`() {
      payload.assertJsonPathIsArray("personReference.identifiers").hasSize(1)
      payload.assertJsonPath("personReference.identifiers[0].type").isEqualTo("NOMS")
      payload.assertJsonPath("personReference.identifiers[0].value").isEqualTo("A1234BC")
    }

    @Test
    fun `additional information will contain the correct fields`() {
      payload.assertJsonPath("additionalInformation.bookingId").isEqualTo("43124234")
    }

    @Test
    fun `will describe the event as a prisoner activity update`() {
      payload.assertJsonPath("description")
        .isEqualTo("A prisoner's sentence dates have been changed")
    }

    @Test
    fun `will add correct fields to telemetry event`() {
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2022-12-04T10:00:00Z")
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234BC")
      assertThat(telemetryAttributes).containsEntry("sentenceCalculationId", "0")
    }

    @Test
    fun `will contain no other telemetry properties`() {
      assertThat(telemetryAttributes).containsOnlyKeys(
        "eventType",
        "occurredAt",
        "publishedAt",
        "nomsNumber",
        "bookingId",
        "sentenceCalculationId",
      )
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  internal inner class PrisonerContactEvents {
    private lateinit var payload: String
    private lateinit var telemetryAttributes: Map<String, String>

    private fun processXtagEvent(eventType: String, approved: Boolean = true) {
      Mockito.reset(hmppsEventSnsClient, telemetryClient)
      whenever(hmppsEventSnsClient.publish(any<PublishRequest>())).thenReturn(CompletableFuture.completedFuture(PublishResponse.builder().build()))

      emitter.convertAndSendWhenSignificant(
        eventType,
        // language=JSON
        """
        {
          "nomisEventType":"$eventType",
          "contactId":7550868,
          "eventDatetime":"2022-12-04T10:00:00",
          "offenderIdDisplay":"A1234BC",
          "personId":4729911,
          "approvedVisitor":"$approved",
          "eventType":"$eventType",
          "auditModuleName":"OIDVIRES",
          "username": "J_SMITH",
          "bookingId":1215922
        }
        """.trimIndent(),
      )
      argumentCaptor<PublishRequest>().apply {
        verify(hmppsEventSnsClient).publish(capture())
        payload = firstValue.message()
      }
      argumentCaptor<Map<String, String>>().apply {
        verify(telemetryClient, times(1)).trackEvent(
          any(),
          capture(),
          isNull(),
        )
        telemetryAttributes = firstValue
      }
    }

    private fun processNullPersonIdXtagEvent(eventType: String, approved: Boolean = true) {
      Mockito.reset(hmppsEventSnsClient, telemetryClient)
      emitter.convertAndSendWhenSignificant(
        eventType,
        // language=JSON
        """
        {
          "nomisEventType":"$eventType",
          "contactId":7550868,
          "eventDatetime":"2022-12-04T10:00:00",
          "offenderIdDisplay":"A1234BC",
          "approvedVisitor":"$approved",
          "eventType":"$eventType",
          "auditModuleName":"OIDVIRES",
          "username": "J_SMITH",
          "bookingId":1215922
        }
        """.trimIndent(),
      )
      argumentCaptor<PublishRequest>().apply {
        verifyNoInteractions(hmppsEventSnsClient)
      }
    }

    @ParameterizedTest
    @MethodSource("contactEventMap")
    fun `will raise a contact event type`(prisonEventType: String, eventType: String, approved: Boolean) {
      processXtagEvent(prisonEventType, approved = approved)
      payload.assertJsonPath("eventType")
        .isEqualTo(eventType)
    }

    @ParameterizedTest
    @MethodSource("contactEventMap")
    fun `will use event datetime for occurred at time`(prisonEventType: String, eventType: String) {
      processXtagEvent(prisonEventType)
      payload.assertJsonPath("occurredAt").isEqualTo("2022-12-04T10:00:00Z")
    }

    @ParameterizedTest
    @MethodSource("contactEventMap")
    fun `will use current time as publishedAt`(prisonEventType: String) {
      processXtagEvent(prisonEventType)
      payload.assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, SECONDS))
    }

    @ParameterizedTest
    @MethodSource("contactEventMap")
    fun `person reference will contain nomsId as NOMS identifier`(prisonEventType: String) {
      processXtagEvent(prisonEventType)
      payload.assertJsonPathIsArray("personReference.identifiers").hasSize(1)
      payload.assertJsonPath("personReference.identifiers[0].type").isEqualTo("NOMS")
      payload.assertJsonPath("personReference.identifiers[0].value").isEqualTo("A1234BC")
    }

    @ParameterizedTest
    @MethodSource("contactEventMap")
    fun `additional information will contain the NOMIS ids of the contact`(prisonEventType: String) {
      processXtagEvent(prisonEventType)
      payload.assertJsonPath("additionalInformation.bookingId").isEqualTo("1215922")
      payload.assertJsonPath("additionalInformation.contactId").isEqualTo("7550868")
      payload.assertJsonPath("additionalInformation.personId").isEqualTo("4729911")
      payload.assertJsonPath("additionalInformation.approvedVisitor").isEqualTo("true")
      payload.assertJsonPath("additionalInformation.username").isEqualTo("J_SMITH")
    }

    @ParameterizedTest
    @MethodSource("contactEventMap")
    fun `will add correct fields to telemetry event`(prisonEventType: String) {
      processXtagEvent(prisonEventType)
      assertThat(telemetryAttributes).containsEntry("occurredAt", "2022-12-04T10:00:00Z")
      assertThat(telemetryAttributes).containsEntry("nomsNumber", "A1234BC")
      assertThat(telemetryAttributes).containsEntry("personId", "4729911")
      assertThat(telemetryAttributes).containsEntry("contactId", "7550868")
    }

    @ParameterizedTest
    @MethodSource("contactEventMap")
    fun `will not raise an event for a null personId`(prisonEventType: String, eventType: String, approved: Boolean) {
      processNullPersonIdXtagEvent(prisonEventType, approved = approved)
    }

    private fun contactEventMap() = listOf(
      Arguments.of("OFFENDER_CONTACT-INSERTED", "prison-offender-events.prisoner.contact-added", true),
      Arguments.of("OFFENDER_CONTACT-INSERTED", "prison-offender-events.prisoner.contact-added", false),
      Arguments.of("OFFENDER_CONTACT-UPDATED", "prison-offender-events.prisoner.contact-approved", true),
      Arguments.of("OFFENDER_CONTACT-UPDATED", "prison-offender-events.prisoner.contact-unapproved", false),
      Arguments.of("OFFENDER_CONTACT-DELETED", "prison-offender-events.prisoner.contact-removed", true),
      Arguments.of("OFFENDER_CONTACT-DELETED", "prison-offender-events.prisoner.contact-removed", false),
    )
  }

  companion object {
    @JvmStatic
    private fun eventMap() = listOf(
      Arguments.of("OFFENDER_MOVEMENT-DISCHARGE", "prison-offender-events.prisoner.released"),
      Arguments.of("OFFENDER_MOVEMENT-RECEPTION", "prison-offender-events.prisoner.received"),
    )
  }
}
