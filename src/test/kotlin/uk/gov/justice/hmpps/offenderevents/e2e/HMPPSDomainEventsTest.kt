package uk.gov.justice.hmpps.offenderevents.e2e

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import junit.framework.AssertionFailedError
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.assertj.core.api.ThrowingConsumer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import uk.gov.justice.hmpps.offenderevents.resource.QueueListenerIntegrationTest
import uk.gov.justice.hmpps.offenderevents.services.wiremock.HMPPSAuthExtension
import uk.gov.justice.hmpps.offenderevents.services.wiremock.PrisonApiExtension
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ExecutionException

@ExtendWith(PrisonApiExtension::class, HMPPSAuthExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HMPPSDomainEventsTest : QueueListenerIntegrationTest() {
  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @BeforeEach
  fun setUp() {
    purgeQueues()
  }

  @Throws(ExecutionException::class, InterruptedException::class)
  private fun geMessagesCurrentlyOnTestQueue(): List<String> {
    val messageResult = prisonEventTestQueueSqsClient.receiveMessage(
      ReceiveMessageRequest.builder().queueUrl(prisonEventTestQueueUrl).build(),
    ).get()
    return messageResult
      .messages()
      .stream()
      .map { obj: Message -> obj.body() }
      .map { message: String -> toSQSMessage(message) }
      .map(SQSMessage::Message)
      .toList()
  }

  @Throws(ExecutionException::class, InterruptedException::class)
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
      @DisplayName("will publish OFFENDER_MOVEMENT-RECEPTION prison event")
      @Throws(
        ExecutionException::class,
        InterruptedException::class,
      )
      fun willPublishPrisonEvent() {
        await().until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 1 }
        val prisonEventMessages = geMessagesCurrentlyOnTestQueue()
        assertThat(prisonEventMessages)
          .singleElement()
          .satisfies(
            ThrowingConsumer { event: String? ->
              assertThatJson(event)
                .node("eventType")
                .isEqualTo("OFFENDER_MOVEMENT-RECEPTION")
            },
          )
      }

      @Test
      @DisplayName("will publish prison-offender-events.prisoner.received HMPPS domain event")
      @Throws(
        ExecutionException::class,
        InterruptedException::class,
      )
      fun willPublishHMPPSDomainEvent() {
        await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
        val hmppsEventMessages = geMessagesCurrentlyOnHMPPSTestQueue()
        assertThat(hmppsEventMessages).singleElement().satisfies(
          ThrowingConsumer { event: String? ->
            assertThatJson(event).node("eventType").isEqualTo("prison-offender-events.prisoner.received")
            assertThatJson(event).node("occurredAt").asString()
              .satisfies(
                ThrowingConsumer { dateTime: String? ->
                  assertThat(dateTime).isEqualTo("2021-06-08T14:41:11.526762+01:00")
                },
              )
            assertThatJson(event).node("publishedAt").asString()
              .satisfies(
                ThrowingConsumer { dateTime: String? ->
                  assertThat(OffsetDateTime.parse(dateTime))
                    .isCloseTo(OffsetDateTime.now(), within(10, ChronoUnit.SECONDS))
                },
              )
            assertThatJson(event).node("additionalInformation.reason").isEqualTo("ADMISSION")
            assertThatJson(event).node("additionalInformation.prisonId").isEqualTo("MDI")
            assertThatJson(event).node("additionalInformation.currentLocation").isEqualTo("IN_PRISON")
            assertThatJson(event)
              .node("additionalInformation.currentPrisonStatus")
              .isEqualTo("UNDER_PRISON_CARE")
          },
        )
      }
    }
  }

  @Nested
  internal inner class ReleasePrisoner {
    @BeforeEach
    fun setUp() {
      sendToTopic(
        "OFFENDER_MOVEMENT-DISCHARGE",
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
      @DisplayName("will publish OFFENDER_MOVEMENT-DISCHARGE prison event")
      @Throws(
        ExecutionException::class,
        InterruptedException::class,
      )
      fun willPublishPrisonEvent() {
        await().until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 1 }
        val prisonEventMessages = geMessagesCurrentlyOnTestQueue()
        assertThat(prisonEventMessages)
          .singleElement()
          .satisfies(
            ThrowingConsumer { event: String? ->
              assertThatJson(event)
                .node("eventType")
                .isEqualTo("OFFENDER_MOVEMENT-DISCHARGE")
            },
          )
      }

      @Test
      @DisplayName("will publish prison-offender-events.prisoner.released HMPPS domain event")
      @Throws(
        ExecutionException::class,
        InterruptedException::class,
      )
      fun willPublishHMPPSDomainEvent() {
        await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
        val hmppsEventMessages = geMessagesCurrentlyOnHMPPSTestQueue()
        assertThat(hmppsEventMessages).singleElement().satisfies(
          ThrowingConsumer { event: String? ->
            assertThatJson(event).node("eventType").isEqualTo("prison-offender-events.prisoner.released")
            assertThatJson(event).node("occurredAt").asString()
              .satisfies(
                ThrowingConsumer { dateTime: String? ->
                  assertThat(dateTime).isEqualTo("2021-02-08T14:41:11.526762Z")
                },
              )
            assertThatJson(event).node("publishedAt").asString()
              .satisfies(
                ThrowingConsumer { dateTime: String? ->
                  assertThat(OffsetDateTime.parse(dateTime))
                    .isCloseTo(OffsetDateTime.now(), within(10, ChronoUnit.SECONDS))
                },
              )
            assertThatJson(event).node("additionalInformation.reason").isEqualTo("TRANSFERRED")
            assertThatJson(event).node("additionalInformation.prisonId").isEqualTo("WWA")
            assertThatJson(event).node("additionalInformation.currentLocation")
              .isEqualTo("BEING_TRANSFERRED")
            assertThatJson(event)
              .node("additionalInformation.currentPrisonStatus")
              .isEqualTo("NOT_UNDER_PRISON_CARE")
          },
        )
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
      @Throws(
        ExecutionException::class,
        InterruptedException::class,
      )
      fun willPublishHMPPSDomainEvent() {
        await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
        val hmppsEventMessages = geMessagesCurrentlyOnHMPPSTestQueue()
        assertThat(hmppsEventMessages).singleElement().satisfies(
          ThrowingConsumer { event: String? ->
            assertThatJson(event).node("eventType").isEqualTo("prison-offender-events.prisoner.released")
            assertThatJson(event).node("additionalInformation.reason").isEqualTo("RELEASED")
            assertThatJson(event).node("additionalInformation.prisonId").isEqualTo("MDI")
            assertThatJson(event).node("additionalInformation.currentLocation")
              .isEqualTo("OUTSIDE_PRISON")
            assertThatJson(event)
              .node("additionalInformation.currentPrisonStatus")
              .isEqualTo("NOT_UNDER_PRISON_CARE")
          },
        )
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
        // send control message so we can check messages have been processed
        sendToTopic(
          "OFFENDER_BOOKING-REASSIGNED",
          // language=JSON
          """
            {
              "eventType":"OFFENDER_BOOKING-REASSIGNED",
              "bookingId":"1201234",
              "eventDatetime":"2024-07-08T14:28:10.0000000Z",
              "offenderIdDisplay":"A9999CA",
              "nomisEventType":"OFF_BKB_UPD",
              "offenderId":"2620073",
              "previousOffenderIdDisplay":"A5694DR",
              "previousOffenderId":"5260560"
            }
            """
            .trimIndent(),
        )

        await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
      }

      @Test
      fun `will not publish a merge event only control message`() {
        await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
        val hmppsEventMessages = geMessagesCurrentlyOnHMPPSTestQueue()
        assertThat(hmppsEventMessages).hasSize(1)
        val domainEvent = hmppsEventMessages.first()
        assertThatJson(domainEvent).node("eventType").isEqualTo("prison-offender-events.prisoner.booking.moved")
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
        // send control message so we can check messages have been processed
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
        await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
      }

      @Test
      fun `will not publish a merge event only control message`() {
        val hmppsEventMessages = geMessagesCurrentlyOnHMPPSTestQueue()
        assertThat(hmppsEventMessages).hasSize(1)
        val domainEvent = hmppsEventMessages.first()
        assertThatJson(domainEvent).node("eventType").isEqualTo("prison-offender-events.prisoner.booking.moved")
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
        // send control message so we can check messages have been processed
        sendToTopic(
          "OFFENDER_BOOKING-REASSIGNED",
          // language=JSON
          """
            {
              "eventType":"OFFENDER_BOOKING-REASSIGNED",
              "bookingId":"1201234",
              "eventDatetime":"2021-02-08T14:41:11.526762Z",
              "offenderIdDisplay":"A9999CA",
              "nomisEventType":"OFF_BKB_UPD",
              "offenderId":"2620073",
              "previousOffenderIdDisplay":"A5694DR",
              "previousOffenderId":"5260560"
            }
            """
            .trimIndent(),
        )
        await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 2 }
      }

      @Test
      fun `will publish a merge event (along with a control message)`() {
        val hmppsEventMessages = geMessagesCurrentlyOnHMPPSTestQueue()
        assertThat(hmppsEventMessages).hasSize(1)
        val mergeDomainEvent = hmppsEventMessages.first()

        assertThatJson(mergeDomainEvent).node("eventType").isEqualTo("prison-offender-events.prisoner.merged")
        assertThatJson(mergeDomainEvent).node("occurredAt").asString()
          .satisfies(
            ThrowingConsumer { dateTime: String? ->
              assertThat(dateTime).isEqualTo("2022-11-02T00:39:05.070936Z")
            },
          )
        assertThatJson(mergeDomainEvent).node("publishedAt").asString()
          .satisfies(
            ThrowingConsumer { dateTime: String? ->
              assertThat(OffsetDateTime.parse(dateTime))
                .isCloseTo(OffsetDateTime.now(), within(10, ChronoUnit.SECONDS))
            },
          )
        assertThatJson(mergeDomainEvent).node("additionalInformation.reason").isEqualTo("MERGE")
        assertThatJson(mergeDomainEvent).node("additionalInformation.removedNomsNumber").isEqualTo("A5694DR")
        assertThatJson(mergeDomainEvent).node("additionalInformation.bookingId").asString().isEqualTo("1201234")
      }
    }
  }

  @Nested
  internal inner class NonExistentOffenders {
    @BeforeEach
    fun setUp() {
      sendToTopic(
        "OFFENDER_MOVEMENT-RECEPTION",
        """
                    { "eventType": "OFFENDER_MOVEMENT-RECEPTION", "eventDatetime": "2023-03-31T13:49:16", "offenderIdDisplay": "NONEXISTENT" }
                
        """.trimIndent(),
      )
      sendToTopic(
        "OFFENDER_MOVEMENT-DISCHARGE",
        """
                    { "eventType": "OFFENDER_MOVEMENT-DISCHARGE", "eventDatetime": "2023-03-31T13:49:16", "offenderIdDisplay": "NONEXISTENT" }
                
        """.trimIndent(),
      )
      PrisonApiExtension.server.stubPrisonerDetails404("NONEXISTENT")
    }

    @Test
    @DisplayName("will ignore a deleted or non-existent offender")
    fun willIgnoreNonExistentOffender() {
      // Wait for messages to have been sent to the prisoneventqueue
      await().until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 2 }
      // Wait for messages to have been consumed by JMS
      await().until { getNumberOfMessagesCurrentlyOnPrisonEventQueue() == 0 }
      // Check that no hmpps event messages were generated from them
      assertThat(getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue()).isEqualTo(0)
      PrisonApiExtension.server.verifyPrisonerDetails404("NONEXISTENT")
    }
  }

  @Nested
  internal inner class CaseNote {
    @BeforeEach
    fun setUp() {
      sendToTopic(
        "OFFENDER_CASE_NOTES-INSERTED",
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
      // Only the one original message sent by the test
      // assertThat(getNumberOfMessagesCurrentlyOnPrisonEventTestQueue()).isEqualTo(1);
      await().until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 1 }
    }

    @Test
    @DisplayName("will publish prison.case-note.published HMPPS domain event")
    @Throws(
      ExecutionException::class,
      InterruptedException::class,
    )
    fun willPublishHMPPSDomainEvent() {
      await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
      val hmppsEventMessages = geMessagesCurrentlyOnHMPPSTestQueue()
      assertThat(hmppsEventMessages).singleElement().satisfies(
        ThrowingConsumer { event: String? ->
          assertThatJson(event).node("eventType").isEqualTo("prison.case-note.published")
          assertThatJson(event).node("occurredAt").asString()
            .satisfies(
              ThrowingConsumer { dateTime: String? ->
                assertThat(dateTime).isEqualTo("2022-11-02T00:39:05.070936Z")
              },
            )
          assertThatJson(event).node("publishedAt").asString()
            .satisfies(
              ThrowingConsumer { dateTime: String? ->
                assertThat(OffsetDateTime.parse(dateTime))
                  .isCloseTo(OffsetDateTime.now(), within(10, ChronoUnit.SECONDS))
              },
            )
          assertThatJson(event).node("detailUrl")
            .isEqualTo("http://localhost:8088/case-notes/A1234AM/1301234")
          assertThatJson(event).node("additionalInformation.caseNoteType").isEqualTo("ALERT-ACTIVE")
          assertThatJson(event).node("additionalInformation.caseNoteId").isEqualTo("\"1301234\"")
        },
      )
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
        // Wait for messages to have been sent to the prisoneventqueue
        await().until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 1 }
        // Wait for messages to have been consumed by JMS
        await().until { getNumberOfMessagesCurrentlyOnPrisonEventQueue() == 0 }
        // Check that no hmpps event messages were generated from them
        assertThat(getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue()).isEqualTo(0)
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
        val hmppsEventMessages = geMessagesCurrentlyOnHMPPSTestQueue()
        assertThat(hmppsEventMessages).singleElement().satisfies(
          ThrowingConsumer { event: String? ->
            assertThatJson(event).node("eventType").isEqualTo("prison-offender-events.prisoner.booking.moved")
            assertThatJson(event).node("additionalInformation.movedToNomsNumber").isEqualTo("A9999CA")
            assertThatJson(event).node("additionalInformation.movedFromNomsNumber").isEqualTo("A1111CL")
            assertThatJson(event).node("personReference.identifiers[0].value").isEqualTo("A9999CA")
            assertThatJson(event).node("personReference.identifiers[0].type").isEqualTo("NOMS")
            assertThatJson(event).node("additionalInformation.bookingId").asString().isEqualTo("2936648")
          },
        )
      }
    }
  }
}
