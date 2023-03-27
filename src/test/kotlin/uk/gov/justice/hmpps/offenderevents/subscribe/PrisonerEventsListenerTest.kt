package uk.gov.justice.hmpps.offenderevents.subscribe

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.listener.QueueAttributes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent
import uk.gov.justice.hmpps.offenderevents.services.HMPPSDomainEventsEmitter
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@JsonTest
internal class PrisonerEventsListenerTest {
  @Autowired
  private lateinit var objectMapper: ObjectMapper

  private val hmppsQueueService: HmppsQueueService = mock()
  private val eventsEmitter: HMPPSDomainEventsEmitter = mock()

  private lateinit var listener: PrisonerEventsListener

  private val message: QueueAttributes = mock()

  @BeforeEach
  fun setUp() {
    listener =
      PrisonerEventsListener(objectMapper, eventsEmitter, hmppsQueueService, Duration.ofMinutes(45), Duration.ofMinutes(15))
  }

  @Nested
  internal inner class MessageOlderThanFortyFiveMinutes {
    @Test
    @DisplayName("Will pass offender event to events emitter")
    fun willPassOffenderEventToEventsEmitter() {
      listener.onPrisonerEvent(createMessage("OFFENDER_MOVEMENT-RECEPTION", "2021-06-08T14:41:11.526762Z"), message)
      argumentCaptor<OffenderEvent>().apply {
        verify(eventsEmitter).convertAndSendWhenSignificant(capture())
        assertThat(firstValue.eventType).isEqualTo("OFFENDER_MOVEMENT-RECEPTION")
        assertThat(firstValue.offenderIdDisplay).isEqualTo("A5194DY")
      }
    }

    @Test
    @DisplayName("will process message if published more than 45 minutes ago")
    fun willProcessMessageIfPublishedMoreThan45MinutesAgo() {
      val fortyFiveMinutesAgo = OffsetDateTime
        .now()
        .minusMinutes(45)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

      listener.onPrisonerEvent(createMessage("OFFENDER_MOVEMENT-RECEPTION", fortyFiveMinutesAgo), message)

      argumentCaptor<OffenderEvent>().apply {
        verify(eventsEmitter).convertAndSendWhenSignificant(capture())
      }
    }
  }

  @Nested
  internal inner class MessageYoungerThanFortyFiveMinutes {
    private var receptionMessageBody: String? = null
    private val prisonEventQueueSqsClient = mock<SqsAsyncClient>()
    private val prisonEventQueueSqsDlqClient = mock<SqsAsyncClient>()

    @BeforeEach
    fun setUp() {
      whenever(message.queueUrl).thenReturn("https://aws.queue/my-queue")
      whenever(hmppsQueueService.findByQueueId("prisoneventqueue")).thenReturn(
        HmppsQueue(
          "prisoneventqueue",
          prisonEventQueueSqsClient,
          "prison-event-queue",
          prisonEventQueueSqsDlqClient,
          "prison-event-dlq",
        ),
      )

      val fortyFourMinutesAgo = OffsetDateTime
        .now()
        .minusMinutes(44)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

      receptionMessageBody = createMessage("OFFENDER_MOVEMENT-RECEPTION", fortyFourMinutesAgo)

      listener.onPrisonerEvent(receptionMessageBody, message)
    }

    @Test
    @DisplayName("will not process message")
    fun willNotProcessMessage() {
      Mockito.verifyNoInteractions(eventsEmitter)
    }

    @Test
    @DisplayName("will resend message")
    fun willResendMessage() {
      argumentCaptor<SendMessageRequest>().apply {
        verify(prisonEventQueueSqsClient).sendMessage(capture())
      }
    }

    @Test
    @DisplayName("will set visibility on message to 15 minutes")
    fun willSetVisibilityOnMessageTo15Minutes() {
      argumentCaptor<SendMessageRequest>().apply {
        verify(prisonEventQueueSqsClient).sendMessage(capture())
        assertThat(firstValue.delaySeconds()).isEqualTo(15 * 60)
      }
    }

    @Test
    @DisplayName("will resend message back to queue it came from")
    fun willResendMessageBackToQueueItCameFrom() {
      argumentCaptor<SendMessageRequest>().apply {
        verify(prisonEventQueueSqsClient).sendMessage(capture())
        assertThat(firstValue.queueUrl()).isEqualTo("https://aws.queue/my-queue")
      }
    }

    @Test
    @DisplayName("message will be sent untouched")
    fun messageWillBeSentUntouched() {
      argumentCaptor<SendMessageRequest>().apply {
        verify(prisonEventQueueSqsClient).sendMessage(capture())
        assertThat(firstValue.messageBody()).isEqualTo(receptionMessageBody)
      }
    }
  }

  @Nested
  internal inner class CaseNotesMessage {
    private var caseNoteMessageBody: String? = null
    private val prisonEventQueueSqsClient = mock<SqsAsyncClient>()
    private val prisonEventQueueSqsDlqClient = mock<SqsAsyncClient>()

    @BeforeEach
    fun setUp() {
      whenever(message.queueUrl).thenReturn("https://aws.queue/my-queue")
      whenever(hmppsQueueService.findByQueueId("prisoneventqueue")).thenReturn(
        HmppsQueue(
          "prisoneventqueue",
          prisonEventQueueSqsClient,
          "prison-event-queue",
          prisonEventQueueSqsDlqClient,
          "prison-event-dlq",
        ),
      )

      val oneMinuteAgo = OffsetDateTime
        .now()
        .minusMinutes(1)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

      caseNoteMessageBody = createMessage("OFFENDER_CASE_NOTES-INSERTED", oneMinuteAgo)

      listener.onPrisonerEvent(caseNoteMessageBody, message)
    }

    @Test
    @DisplayName("will process message")
    fun willProcessMessage() {
      argumentCaptor<OffenderEvent>().apply {
        verify(eventsEmitter).convertAndSendWhenSignificant(capture())
      }
    }
  }
}

fun createMessage(type: String, publishedAt: String): String =
  """
    {
      "Type" : "Notification",
      "MessageId" : "670250d8-9806-5670-bdbb-09e1ede37855",
      "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
      "Message" : "{\"eventType\":\"$type\",\"eventDatetime\":\"2021-06-08T14:41:11.526762\",\"offenderIdDisplay\":\"A5194DY\",\"bookingId\":1201234,\"movementSeq\":11,\"nomisEventType\":\"OFF_RECEP_OASYS\"}",
      "Timestamp" : "2021-06-08T13:41:57.913Z",
      "SignatureVersion" : "1",
      "Signature" : "TcHxCUITm+AeySkOmm2bFYuubdfhIl4S0cmw2J+rVF/jvq4G85+mAWY7pFXnaf4N08JPW3hgFjK/ulPT9kU/+eCHvbO03RDkeUppSiGQLxgDzCYq19TroREkWmPccYYVS3lPwLZMtzMxiyJbKpV3QmQIGRucb9c0FF+p/7vT98ebOkq+8a5XbsTSg9jO5Y+i4lmHm+Dr8J+PeY4DS6+JzT7f4Tv3C6f5J63seryXwqRggsj6aVBIUAu0bgsK0hbWtVApo3Jisx/0IaJBvRrYD2E5tBQwKxDR6YW0ZnKrMCjhSTRl9FUyQltf0vexGjaKhkgDrZQvtR6MSc/0007VFw==",
      "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-010a507c1833636cd94bdb98bd93083a.pem",
      "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:996229b2-8a4d-4801-9709-319c9c68dcd8",
      "MessageAttributes" : {
        "eventType" : {"Type":"String","Value":"$type"},
        "id" : {"Type":"String","Value":"fe7c603f-4549-324e-a1bf-b6addcb87edd"},
        "contentType" : {"Type":"String","Value":"text/plain;charset=UTF-8"},
        "timestamp" : {"Type":"Number.java.lang.Long","Value":"1623159717906"},
        "publishedAt" : {"Type":"String","Value":"$publishedAt"}
      }
    }
  """
