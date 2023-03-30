package uk.gov.justice.hmpps.offenderevents.e2e

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import junit.framework.AssertionFailedError
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.assertj.core.api.ThrowingConsumer
import org.awaitility.Awaitility
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
import uk.gov.justice.hmpps.offenderevents.services.wiremock.CommunityApiExtension
import uk.gov.justice.hmpps.offenderevents.services.wiremock.HMPPSAuthExtension
import uk.gov.justice.hmpps.offenderevents.services.wiremock.PrisonApiExtension
import uk.gov.justice.hmpps.offenderevents.services.wiremock.PrisonApiMockServer.MovementFragment
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ExecutionException

@ExtendWith(PrisonApiExtension::class, CommunityApiExtension::class, HMPPSAuthExtension::class)
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

  private fun toSQSMessage(message: String): SQSMessage {
    return try {
      objectMapper.readValue(message, SQSMessage::class.java)
    } catch (e: JsonProcessingException) {
      throw AssertionFailedError(String.format("Message %s is not parseable", message))
    }
  }

  @JvmRecord
  internal data class SQSMessage(val Message: String)

  private fun sendToTopic(eventType: String, payload: String) {
    val attributes = mapOf(
      "eventType" to MessageAttributeValue.builder().dataType("String").stringValue(eventType).build(),
      "publishedAt" to MessageAttributeValue.builder().dataType("String").stringValue("2021-06-08T14:41:14Z")
        .build(), // LocalDateTime.now().format(DateTimeFormatter.ISO_INSTANT)
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
        Awaitility.await().until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 1 }
        Awaitility.await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
      }

      @Test
      @DisplayName("will publish OFFENDER_MOVEMENT-RECEPTION prison event")
      @Throws(
        ExecutionException::class,
        InterruptedException::class,
      )
      fun willPublishPrisonEvent() {
        Awaitility.await().until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 1 }
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
      @DisplayName("will publish prison-offender-events.prisoner.received HMPPS domain event without asking community-api")
      @Throws(
        ExecutionException::class,
        InterruptedException::class,
      )
      fun willPublishHMPPSDomainEvent() {
        Awaitility.await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
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
            assertThatJson(event).node("additionalInformation.probableCause").isEqualTo("RECALL")
            assertThatJson(event).node("additionalInformation.prisonId").isEqualTo("MDI")
            assertThatJson(event).node("additionalInformation.source").isEqualTo("PRISON")
            assertThatJson(event).node("additionalInformation.currentLocation").isEqualTo("IN_PRISON")
            assertThatJson(event)
              .node("additionalInformation.currentPrisonStatus")
              .isEqualTo("UNDER_PRISON_CARE")
          },
        )
        CommunityApiExtension.server.verify(0, getRequestedFor(WireMock.anyUrl()))
      }
    }

    @Nested
    internal inner class WhenIsReportedAsSentenced {
      @BeforeEach
      fun setUp() {
        PrisonApiExtension.server.stubPrisonerDetails("A5194DY", "SENTENCED", false, "ADM", "K", "ACTIVE IN", "MDI")
      }

      @Test
      @DisplayName("will publish prison-offender-events.prisoner.received HMPPS domain event by asking community-api")
      @Throws(
        ExecutionException::class,
        InterruptedException::class,
      )
      fun willPublishHMPPSDomainEvent() {
        CommunityApiExtension.server.stubForNoRecall("A5194DY")
        Awaitility.await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
        val hmppsEventMessages = geMessagesCurrentlyOnHMPPSTestQueue()
        assertThat(hmppsEventMessages).singleElement().satisfies(
          ThrowingConsumer { event: String? ->
            assertThatJson(event).node("eventType").isEqualTo("prison-offender-events.prisoner.received")
            assertThatJson(event).node("additionalInformation.reason").isEqualTo("ADMISSION")
            assertThatJson(event).node("additionalInformation.probableCause").isEqualTo("CONVICTED")
            assertThatJson(event).node("additionalInformation.prisonId").isEqualTo("MDI")
            assertThatJson(event).node("additionalInformation.source").isEqualTo("PRISON")
          },
        )
        CommunityApiExtension.server.verify(getRequestedFor(urlEqualTo("/secure/offenders/nomsNumber/A5194DY/convictions/active/nsis/recall")))
      }

      @Test
      @DisplayName("will publish a recalled  prison-offender-events.prisoner.received HMPPS domain event when community-api indicates a recall")
      @Throws(
        ExecutionException::class,
        InterruptedException::class,
      )
      fun willPublishRecallHMPPSDomainEvent() {
        CommunityApiExtension.server.stubForRecall("A5194DY")
        PrisonApiExtension.server.stubMovements(
          "A5194DY",
          listOf(MovementFragment("IN", LocalDateTime.now())),
        )
        Awaitility.await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
        val hmppsEventMessages = geMessagesCurrentlyOnHMPPSTestQueue()
        assertThat(hmppsEventMessages).singleElement().satisfies(
          ThrowingConsumer { event: String? ->
            assertThatJson(event).node("eventType").isEqualTo("prison-offender-events.prisoner.received")
            assertThatJson(event).node("additionalInformation.reason").isEqualTo("ADMISSION")
            assertThatJson(event).node("additionalInformation.probableCause").isEqualTo("RECALL")
            assertThatJson(event).node("additionalInformation.prisonId").isEqualTo("MDI")
            assertThatJson(event).node("additionalInformation.source").isEqualTo("PROBATION")
          },
        )
        CommunityApiExtension.server.verify(getRequestedFor(urlEqualTo("/secure/offenders/nomsNumber/A5194DY/convictions/active/nsis/recall")))
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
        Awaitility.await().until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 1 }
        Awaitility.await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
      }

      @Test
      @DisplayName("will publish OFFENDER_MOVEMENT-DISCHARGE prison event")
      @Throws(
        ExecutionException::class,
        InterruptedException::class,
      )
      fun willPublishPrisonEvent() {
        Awaitility.await().until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 1 }
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
        Awaitility.await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
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
            assertThatJson(event).node("additionalInformation.probableCause").isAbsent()
            assertThatJson(event).node("additionalInformation.prisonId").isEqualTo("WWA")
            assertThatJson(event).node("additionalInformation.currentLocation")
              .isEqualTo("BEING_TRANSFERRED")
            assertThatJson(event)
              .node("additionalInformation.currentPrisonStatus")
              .isEqualTo("NOT_UNDER_PRISON_CARE")
          },
        )
        CommunityApiExtension.server.verify(0, getRequestedFor(WireMock.anyUrl()))
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
        Awaitility.await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
        val hmppsEventMessages = geMessagesCurrentlyOnHMPPSTestQueue()
        assertThat(hmppsEventMessages).singleElement().satisfies(
          ThrowingConsumer { event: String? ->
            assertThatJson(event).node("eventType").isEqualTo("prison-offender-events.prisoner.released")
            assertThatJson(event).node("additionalInformation.reason").isEqualTo("RELEASED")
            assertThatJson(event).node("additionalInformation.probableCause").isAbsent()
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
    @BeforeEach
    fun setUp() {
      sendToTopic(
        "BOOKING_NUMBER-CHANGED",
        """
                {
                    "eventType":"BOOKING_NUMBER-CHANGED",
                    "eventDatetime":"2021-02-08T14:41:11.526762",
                    "bookingId":1201234,
                    "previousBookingNumber": "38430A",
                    "nomisEventType":"BOOK_UPD_OASYS"
                    }
                
                """
          .trimIndent(),
      )
    }

    @Nested
    internal inner class WhenIsReportedAsTransfer {
      @BeforeEach
      fun setUp() {
        PrisonApiExtension.server.stubBasicPrisonerDetails("A5194DY", 1201234L)
        PrisonApiExtension.server.stubPrisonerIdentifiers("A5694DR", 1201234L)
      }

      @Test
      @DisplayName("will publish prison event and hmpps domain event for release transfer")
      fun willPublishPrisonEventForTransferRelease() {
        Awaitility.await().until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 1 }
        Awaitility.await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
      }

      @Test
      @DisplayName("will publish BOOKING_NUMBER-CHANGED prison event")
      @Throws(
        ExecutionException::class,
        InterruptedException::class,
      )
      fun willPublishPrisonEvent() {
        Awaitility.await().until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 1 }
        val prisonEventMessages = geMessagesCurrentlyOnTestQueue()
        assertThat(prisonEventMessages)
          .singleElement()
          .satisfies(
            ThrowingConsumer { event: String? ->
              assertThatJson(event)
                .node("eventType")
                .isEqualTo("BOOKING_NUMBER-CHANGED")
            },
          )
      }

      @Test
      @DisplayName("will publish prison-offender-events.prisoner.merged HMPPS domain event")
      @Throws(
        ExecutionException::class,
        InterruptedException::class,
      )
      fun willPublishHMPPSDomainEvent() {
        Awaitility.await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
        val hmppsEventMessages = geMessagesCurrentlyOnHMPPSTestQueue()
        assertThat(hmppsEventMessages).singleElement().satisfies(
          ThrowingConsumer { event: String? ->
            assertThatJson(event).node("eventType").isEqualTo("prison-offender-events.prisoner.merged")
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
            assertThatJson(event).node("additionalInformation.reason").isEqualTo("MERGE")
            assertThatJson(event).node("additionalInformation.removedNomsNumber").isEqualTo("A5694DR")
          },
        )
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
                    { "eventType": "OFFENDER_MOVEMENT-RECEPTION", "offenderIdDisplay": "NONEXISTENT" }
                
        """.trimIndent(),
      )
      sendToTopic(
        "OFFENDER_MOVEMENT-DISCHARGE",
        """
                    { "eventType": "OFFENDER_MOVEMENT-DISCHARGE", "offenderIdDisplay": "NONEXISTENT" }
                
        """.trimIndent(),
      )
      PrisonApiExtension.server.stubPrisonerDetails404("NONEXISTENT")
    }

    @Test
    @DisplayName("will ignore a deleted or non-existent offender")
    fun willIgnoreNonExistentOffender() {
      // Wait for messages to have been sent to the prisoneventqueue
      Awaitility.await().until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 2 }
      // Wait for messages to have been consumed by JMS
      Awaitility.await().until { getNumberOfMessagesCurrentlyOnPrisonEventQueue() == 0 }
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
      Awaitility.await().until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 1 }
    }

    @Test
    @DisplayName("will publish prison.case-note.published HMPPS domain event")
    @Throws(
      ExecutionException::class,
      InterruptedException::class,
    )
    fun willPublishHMPPSDomainEvent() {
      Awaitility.await().until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 1 }
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
}
