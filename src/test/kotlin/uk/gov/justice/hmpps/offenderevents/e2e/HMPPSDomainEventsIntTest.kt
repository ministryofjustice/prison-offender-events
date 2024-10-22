package uk.gov.justice.hmpps.offenderevents.e2e

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import junit.framework.AssertionFailedError
import org.assertj.core.api.Assertions.within
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import uk.gov.justice.hmpps.offenderevents.helpers.assertJsonPath
import uk.gov.justice.hmpps.offenderevents.helpers.assertJsonPathDateTimeIsCloseTo
import uk.gov.justice.hmpps.offenderevents.resource.QueueListenerIntegrationTest
import uk.gov.justice.hmpps.offenderevents.services.HMPPSDomainEventsEmitter
import uk.gov.justice.hmpps.offenderevents.services.wiremock.HMPPSAuthExtension
import uk.gov.justice.hmpps.offenderevents.services.wiremock.PrisonApiExtension
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@ExtendWith(PrisonApiExtension::class, HMPPSAuthExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HMPPSDomainEventsIntTest : QueueListenerIntegrationTest() {
  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @SpyBean
  private lateinit var emitter: HMPPSDomainEventsEmitter

  @BeforeEach
  fun setUp() {
    purgeQueues()
  }

  private fun geMessagesCurrentlyOnHMPPSTestQueue(): List<String> {
    val messageResult = prisonEventTestQueueSqsClient.receiveMessage(
      ReceiveMessageRequest.builder().queueUrl(hmppsEventTestQueueUrl).build(),
    ).get()
    return messageResult
      .messages()
      .stream()
      .map { obj: Message -> obj.body() }
      .map { message: String -> toSQSMessage(message) }
      .map(SQSMessage::Message)
      .toList()
  }

  private fun toSQSMessage(message: String): SQSMessage = try {
    objectMapper.readValue(message, SQSMessage::class.java)
  } catch (e: JsonProcessingException) {
    throw AssertionFailedError(String.format("Message %s is not parseable", message))
  }

  @JvmRecord
  internal data class SQSMessage(val Message: String)

  private fun sendToTopic(eventType: String, payload: String) {
    val attributes = mapOf(
      "eventType" to MessageAttributeValue.builder().dataType("String").stringValue(eventType).build(),
      // LocalDateTime.now().format(DateTimeFormatter.ISO_INSTANT)
      "publishedAt" to MessageAttributeValue.builder().dataType("String").stringValue("2021-06-08T14:41:14Z").build(),
    )
    prisonEventTopicSnsClient.publish(
      PublishRequest.builder().topicArn(prisonEventTopicArn).message(payload)
        .messageAttributes(attributes).build(),
    )
  }

  @Nested
  internal inner class ReceivePrisoner {
    @BeforeEach
    fun setUp() {
      sendToTopic(
        "OFFENDER_MOVEMENT-RECEPTION",
        //language=JSON
        """
          {
               "eventType":"OFFENDER_MOVEMENT-RECEPTION",
              "eventDatetime":"2021-06-08T14:41:11.526762",
              "offenderIdDisplay":"A5194DY",
              "bookingId":1201234,
              "movementSeq":11,
              "nomisEventType":"OFF_RECEP_OASYS"
              }
          
          """
          .trimIndent(),
      )
    }

    @Nested
    internal inner class WhenIsReportedAsRecall {
      @BeforeEach
      fun setUp() {
        PrisonApiExtension.server.stubPrisonerDetails("A5194DY", "RECALL", true, "ADM", "R", "ACTIVE IN", "MDI")
      }

      @Test
      @DisplayName("will publish prison event and hmpps domain event for reception")
      fun willPublishPrisonEventForReception() {
        await().until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 1 }
        await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
      }

      @Test
      @DisplayName("will publish prison-offender-events.prisoner.received HMPPS domain event")
      fun willPublishHMPPSDomainEvent() {
        await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
        val domainEvent = geMessagesCurrentlyOnHMPPSTestQueue().first()

        with(domainEvent) {
          assertJsonPath("eventType", "prison-offender-events.prisoner.received")
          assertJsonPath("occurredAt", "2021-06-08T14:41:11.526762+01:00")
          assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, ChronoUnit.SECONDS))
          assertJsonPath("additionalInformation.reason").isEqualTo("ADMISSION")
          assertJsonPath("additionalInformation.prisonId").isEqualTo("MDI")
          assertJsonPath("additionalInformation.currentLocation").isEqualTo("IN_PRISON")
          assertJsonPath("additionalInformation.currentPrisonStatus")
            .isEqualTo("UNDER_PRISON_CARE")
        }
      }
    }
  }

  @Nested
  internal inner class ReleasePrisoner {
    @BeforeEach
    fun setUp() {
      sendToTopic(
        "OFFENDER_MOVEMENT-DISCHARGE",
        //language=JSON
        """
          {
              "eventType":"OFFENDER_MOVEMENT-DISCHARGE",
              "eventDatetime":"2021-02-08T14:41:11.526762",
              "offenderIdDisplay":"A5194DY",
              "bookingId":1201234,
              "movementSeq":11,
              "nomisEventType":"OFF_DISCH_OASYS"
              }
          
          """
          .trimIndent(),
      )
    }

    @Nested
    internal inner class WhenIsReportedAsTransfer {
      @BeforeEach
      fun setUp() {
        PrisonApiExtension.server.stubPrisonerDetails("A5194DY", "RECALL", true, "TRN", "PROD", "INACTIVE TRN", "WWA")
      }

      @Test
      @DisplayName("will publish prison event and hmpps domain event for release transfer")
      fun willPublishPrisonEventForTransferRelease() {
        await().until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 1 }
        await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
      }

      @Test
      @DisplayName("will publish prison-offender-events.prisoner.released HMPPS domain event")
      fun willPublishHMPPSDomainEvent() {
        await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }

        val domainEvent = geMessagesCurrentlyOnHMPPSTestQueue().first()

        with(domainEvent) {
          assertJsonPath("eventType", "prison-offender-events.prisoner.released")
          assertJsonPath("occurredAt", "2021-02-08T14:41:11.526762Z")
          assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, ChronoUnit.SECONDS))
          assertJsonPath("additionalInformation.reason").isEqualTo("TRANSFERRED")
          assertJsonPath("additionalInformation.prisonId").isEqualTo("WWA")
          assertJsonPath("additionalInformation.currentLocation").isEqualTo("BEING_TRANSFERRED")
          assertJsonPath("additionalInformation.currentPrisonStatus")
            .isEqualTo("NOT_UNDER_PRISON_CARE")
        }
      }
    }

    @Nested
    internal inner class WhenIsReportedAsRelease {
      @BeforeEach
      fun setUp() {
        PrisonApiExtension.server.stubPrisonerDetails("A5194DY", "SENTENCED", false, "REL", "CA", "INACTIVE OUT", "MDI")
      }

      @Test
      @DisplayName("will publish prison-offender-events.prisoner.release HMPPS domain event")
      fun willPublishHMPPSDomainEvent() {
        await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }

        val domainEvent = geMessagesCurrentlyOnHMPPSTestQueue().first()

        with(domainEvent) {
          assertJsonPath("eventType", "prison-offender-events.prisoner.released")
          assertJsonPath("additionalInformation.reason").isEqualTo("RELEASED")
          assertJsonPath("additionalInformation.prisonId").isEqualTo("MDI")
          assertJsonPath("additionalInformation.currentLocation").isEqualTo("OUTSIDE_PRISON")
          assertJsonPath("additionalInformation.currentPrisonStatus")
            .isEqualTo("NOT_UNDER_PRISON_CARE")
        }
      }
    }
  }

  @Nested
  internal inner class MergePrisoner {

    @Nested
    inner class TypeIsBookNumberChanged {
      @BeforeEach
      fun setUp() {
        sendToTopic(
          "BOOKING_NUMBER-CHANGED",
          // language=JSON
          """
            {
              "eventType":"BOOKING_NUMBER-CHANGED",
              "type":"BOOK_NUMBER_CHANGE",
              "bookingId":"2952268",
              "nomisEventType":"P1_RESULT",
              "eventDatetime":"2024-08-30T15:14:06.0000000Z",
              "offenderId":"5109860",
              "bookingNumber":"11141F",
              "previousBookingNumber":"31226G"
            }
           """
            .trimIndent(),
        )

        await().untilAsserted { verify(emitter).sendEvents(any()) }
      }

      @Test
      fun `will not publish a merge event`() {
        verify(emitter).sendEvents(check { it.isEmpty() })
      }
    }

    @Nested
    inner class TypeIsBookNumberChangedDuplicate {
      @BeforeEach
      fun setUp() {
        sendToTopic(
          "BOOKING_NUMBER-CHANGED",
          // language=JSON
          """
            {
              "eventType":"BOOKING_NUMBER-CHANGED",
              "type":"BOOK_NUMBER_CHANGE_DUPLICATE",
              "bookingId":"2952268",
              "nomisEventType":"BOOK_UPD_OASYS",
              "eventDatetime":"2024-08-30T15:14:06.0000000Z",
              "offenderId":"5109860",
              "previousBookingNumber":"31226G"
            }
          """
            .trimIndent(),
        )
        await().untilAsserted { verify(emitter).sendEvents(any()) }
      }

      @Test
      fun `will not publish a merge event`() {
        verify(emitter).sendEvents(check { it.isEmpty() })
      }
    }

    @Nested
    inner class TypeIsMerge {
      @BeforeEach
      fun setUp() {
        sendToTopic(
          "BOOKING_NUMBER-CHANGED",
          // language=JSON
          """
            {
              "eventType":"BOOKING_NUMBER-CHANGED",
              "type":"MERGE",
              "bookingId":"1201234",
              "nomisEventType":"BOOK_UPD_OASYS",
              "eventDatetime":"2022-11-02T00:39:05.0709360Z",
              "offenderIdDisplay":"A0851FE",
              "offenderId":"5282038",
              "previousOffenderIdDisplay":"A5694DR",
              "previousBookingNumber":"35607E"
            }
          """
            .trimIndent(),
        )
        await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
      }

      @Test
      fun `will publish a merge event`() {
        val mergeDomainEvent = geMessagesCurrentlyOnHMPPSTestQueue().first()

        with(mergeDomainEvent) {
          assertJsonPath("eventType", "prison-offender-events.prisoner.merged")
          assertJsonPath("occurredAt", "2022-11-02T00:39:05.070936Z")
          assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, ChronoUnit.SECONDS))
          assertJsonPath("additionalInformation.reason", "MERGE")
          assertJsonPath("additionalInformation.removedNomsNumber", "A5694DR")
          assertJsonPath("additionalInformation.bookingId", "1201234")
        }
      }
    }
  }

  @Nested
  internal inner class NonExistentOffenders {
    @BeforeEach
    fun setUp() {
      sendToTopic(
        "OFFENDER_MOVEMENT-RECEPTION",
        //language=JSON
        """
          { 
            "eventType": "OFFENDER_MOVEMENT-RECEPTION", 
            "eventDatetime": "2023-03-31T13:49:16", 
            "offenderIdDisplay": "NONEXISTENT"
          }
                
        """.trimIndent(),
      )
      sendToTopic(
        "OFFENDER_MOVEMENT-DISCHARGE",
        //language=JSON
        """
        { 
          "eventType": "OFFENDER_MOVEMENT-DISCHARGE", 
          "eventDatetime": "2023-03-31T13:49:16", 
          "offenderIdDisplay": "NONEXISTENT"
        }
        """.trimIndent(),
      )
      PrisonApiExtension.server.stubPrisonerDetails404("NONEXISTENT")
    }

    @Test
    @DisplayName("will ignore a deleted or non-existent offender")
    fun willIgnoreNonExistentOffender() {
      await().untilAsserted {
        verify(emitter, times(2)).sendEvents(check { it.isEmpty() })
      }
      PrisonApiExtension.server.verifyPrisonerDetails404("NONEXISTENT")
    }
  }

  @Nested
  internal inner class CaseNote {
    @BeforeEach
    fun setUp() {
      sendToTopic(
        "OFFENDER_CASE_NOTES-INSERTED",
        //language=JSON
        """
        {
            "eventType": "ALERT-ACTIVE",
            "eventDatetime": "2022-11-02T00:39:05.0709360Z",
            "caseNoteId": 1301234,
            "rootOffenderId": 1259340,
            "offenderIdDisplay": "A1234AM",
            "agencyLocationId": "PVI",
            "caseNoteType": "ALERT",
            "caseNoteSubType": "ACTIVE"
        }
                
                """
          .trimIndent(),
      )
    }

    @Test
    @DisplayName("will not publish prison event")
    fun willNotPublishPrisonEvent() {
      await().untilAsserted {
        verify(emitter).sendEvents(check { it.isEmpty() })
      }
    }

    @Test
    @DisplayName("will publish prison.case-note.published HMPPS domain event")
    fun willPublishHMPPSDomainEvent() {
      await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }

      val domainEvent = geMessagesCurrentlyOnHMPPSTestQueue().first()

      with(domainEvent) {
        assertJsonPath("eventType", "prison.case-note.published")
        assertJsonPath("occurredAt", "2022-11-02T00:39:05.070936Z")
        assertJsonPathDateTimeIsCloseTo("publishedAt", OffsetDateTime.now(), within(10, ChronoUnit.SECONDS))
        assertJsonPath("detailUrl")
          .isEqualTo("http://localhost:8088/case-notes/A1234AM/1301234")
        assertJsonPath("additionalInformation.caseNoteType").isEqualTo("ALERT-ACTIVE")
        assertJsonPath("additionalInformation.caseNoteId").isEqualTo("1301234")
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_BOOKING-REASSIGNED")
  internal inner class OffenderBookingReassigned {
    @Nested
    internal inner class WhenMovedBetweenAliases {
      @BeforeEach
      fun setUp() {
        sendToTopic(
          "OFFENDER_BOOKING-REASSIGNED",
          // language=JSON
          """
          {
            "eventType":"OFFENDER_BOOKING-REASSIGNED",
            "bookingId":"2936648",
            "eventDatetime":"2024-07-08T14:28:10.0000000Z",
            "offenderIdDisplay":"A9999CA",
            "nomisEventType":"OFF_BKB_UPD",
            "offenderId":"2620073",
            "previousOffenderIdDisplay":"A9999CA",
            "previousOffenderId":"5260560"
          }
                """
            .trimIndent(),
        )
      }

      @Test
      fun `will not publish a domain event`() {
        await().untilAsserted {
          verify(emitter).sendEvents(check { it.isEmpty() })
        }
      }
    }

    @Nested
    internal inner class WhenMovedBetweenPrisoners {
      @BeforeEach
      fun setUp() {
        sendToTopic(
          "OFFENDER_BOOKING-REASSIGNED",
          // language=JSON
          """
          {
            "eventType":"OFFENDER_BOOKING-REASSIGNED",
            "bookingId":"2936648",
            "eventDatetime":"2024-07-08T14:28:10.0000000Z",
            "offenderIdDisplay":"A9999CA",
            "nomisEventType":"OFF_BKB_UPD",
            "offenderId":"2620073",
            "previousOffenderIdDisplay":"A1111CL",
            "previousOffenderId":"5260560"
          }
                """
            .trimIndent(),
        )
      }

      @Test
      fun `will publish a domain event`() {
        await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
        val domainEvent = geMessagesCurrentlyOnHMPPSTestQueue().first()

        with(domainEvent) {
          assertJsonPath("eventType", "prison-offender-events.prisoner.booking.moved")
          assertJsonPath("additionalInformation.movedToNomsNumber").isEqualTo("A9999CA")
          assertJsonPath("additionalInformation.movedFromNomsNumber").isEqualTo("A1111CL")
          assertJsonPath("personReference.identifiers[0].value").isEqualTo("A9999CA")
          assertJsonPath("personReference.identifiers[0].type").isEqualTo("NOMS")
          assertJsonPath("additionalInformation.bookingId").asString().isEqualTo("2936648")
        }
      }
    }
  }

  @Nested
  @DisplayName("APPOINTMENT_CHANGED")
  internal inner class AppointmentChangedEvent {
    @BeforeEach
    fun setUp() {
      PrisonApiExtension.server.stubBasicPrisonerDetails(offenderNumber = "A9999CA", bookingId = 2936649)

      sendToTopic(
        "APPOINTMENT_CHANGED",
        // language=JSON
        """
          {
            "eventType": "APPOINTMENT_CHANGED",
            "bookingId": "2936649",
            "eventDatetime": "2024-07-08T14:28:10",
            "nomisEventType": "SCHEDULE_INT_APP-CHANGED",
            "scheduleEventId": "100",
            "scheduleEventClass": "INT_MOV",
            "scheduleEventType": "APP",
            "scheduleEventSubType": "VLB",
            "scheduledStartTime": "2024-07-08T10:15:00",
            "scheduledEndTime": "2024-07-08T10:45:00",
            "scheduleEventStatus": "CANC",
            "recordDeleted": "true",
            "agencyLocationId": "MDI"
          }
          """
          .trimIndent(),
      )
    }

    @Test
    fun `will publish a domain event`() {
      await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
      val domainEvent = geMessagesCurrentlyOnHMPPSTestQueue().first()

      with(domainEvent) {
        assertJsonPath("eventType", "prison-offender-events.prisoner.video-appointment.cancelled")
        assertJsonPath("personReference.identifiers[0].value").isEqualTo("A9999CA")
        assertJsonPath("personReference.identifiers[0].type").isEqualTo("NOMS")
        assertJsonPath("additionalInformation.scheduleEventId").asString().isEqualTo("100")
        assertJsonPath("additionalInformation.scheduleEventSubType").isEqualTo("VLB")
        assertJsonPath("additionalInformation.scheduleEventStatus").isEqualTo("CANC")
        assertJsonPath("additionalInformation.scheduledStartTime").isEqualTo("2024-07-08T10:15")
        assertJsonPath("additionalInformation.scheduledEndTime").isEqualTo("2024-07-08T10:45")
        assertJsonPath("additionalInformation.recordDeleted").isEqualTo("true")
        assertJsonPath("additionalInformation.agencyLocationId").isEqualTo("MDI")
      }
    }
  }
}
