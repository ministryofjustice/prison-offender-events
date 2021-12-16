package uk.gov.justice.hmpps.offenderevents.subscribe

import com.amazon.sqs.javamessaging.message.SQSTextMessage
import com.amazonaws.services.sqs.AmazonSQSAsync
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import lombok.SneakyThrows
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
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

  @Captor
  private lateinit var offenderEventArgumentCaptor: ArgumentCaptor<OffenderEvent>

  @Captor
  private lateinit var sendMessageRequestArgumentCaptor: ArgumentCaptor<SendMessageRequest>

  private lateinit var listener: PrisonerEventsListener

  private val message: SQSTextMessage = mock()

  @BeforeEach
  fun setUp() {
    listener =
      PrisonerEventsListener(objectMapper, eventsEmitter, hmppsQueueService, Duration.ofMinutes(45), Duration.ofMinutes(15))
  }

  @Nested
  internal inner class MessageOlderThanFortyFiveMinutes {
    @SneakyThrows
    @Test
    @DisplayName("Will pass offender event to events emitter")
    fun willPassOffenderEventToEventsEmitter() {
      listener.onPrisonerEvent(
        """
                {
                  "Type" : "Notification",
                  "MessageId" : "670250d8-9806-5670-bdbb-09e1ede37855",
                  "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
                  "Message" : "{\"eventType\":\"OFFENDER_MOVEMENT-RECEPTION\",\"eventDatetime\":\"2021-06-08T14:41:11.526762\",\"offenderIdDisplay\":\"A5194DY\",\"bookingId\":1201234,\"movementSeq\":11,\"nomisEventType\":\"OFF_RECEP_OASYS\"}",
                  "Timestamp" : "2021-06-08T13:41:57.913Z",
                  "SignatureVersion" : "1",
                  "Signature" : "TcHxCUITm+AeySkOmm2bFYuubdfhIl4S0cmw2J+rVF/jvq4G85+mAWY7pFXnaf4N08JPW3hgFjK/ulPT9kU/+eCHvbO03RDkeUppSiGQLxgDzCYq19TroREkWmPccYYVS3lPwLZMtzMxiyJbKpV3QmQIGRucb9c0FF+p/7vT98ebOkq+8a5XbsTSg9jO5Y+i4lmHm+Dr8J+PeY4DS6+JzT7f4Tv3C6f5J63seryXwqRggsj6aVBIUAu0bgsK0hbWtVApo3Jisx/0IaJBvRrYD2E5tBQwKxDR6YW0ZnKrMCjhSTRl9FUyQltf0vexGjaKhkgDrZQvtR6MSc/0007VFw==",
                  "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-010a507c1833636cd94bdb98bd93083a.pem",
                  "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:996229b2-8a4d-4801-9709-319c9c68dcd8",
                  "MessageAttributes" : {
                    "eventType" : {"Type":"String","Value":"OFFENDER_MOVEMENT-RECEPTION"},
                    "id" : {"Type":"String","Value":"fe7c603f-4549-324e-a1bf-b6addcb87edd"},
                    "contentType" : {"Type":"String","Value":"text/plain;charset=UTF-8"},
                    "timestamp" : {"Type":"Number.java.lang.Long","Value":"1623159717906"},
                    "publishedAt" : {"Type":"String","Value":"2021-06-08T14:41:11.526762Z"}
                  }
                }
                """,
        message
      )
      verify(eventsEmitter).convertAndSendWhenSignificant(offenderEventArgumentCaptor.capture())
      assertThat(offenderEventArgumentCaptor.value.eventType).isEqualTo("OFFENDER_MOVEMENT-RECEPTION")
      assertThat(offenderEventArgumentCaptor.value.offenderIdDisplay).isEqualTo("A5194DY")
    }

    @SneakyThrows
    @Test
    @DisplayName("will process message if published more than 45 minutes ago")
    fun willProcessMessageIfPublishedMoreThan45MinutesAgo() {
      val fortyFiveMinutesAgo = OffsetDateTime
        .now()
        .minusMinutes(45)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
      listener.onPrisonerEvent(
        String.format(
          """
                {
                  "Type" : "Notification",
                  "MessageId" : "670250d8-9806-5670-bdbb-09e1ede37855",
                  "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
                  "Message" : "{\"eventType\":\"OFFENDER_MOVEMENT-RECEPTION\",\"eventDatetime\":\"2021-06-08T14:41:11.526762\",\"offenderIdDisplay\":\"A5194DY\",\"bookingId\":1201234,\"movementSeq\":11,\"nomisEventType\":\"OFF_RECEP_OASYS\"}",
                  "Timestamp" : "2021-06-08T13:41:57.913Z",
                  "SignatureVersion" : "1",
                  "Signature" : "TcHxCUITm+AeySkOmm2bFYuubdfhIl4S0cmw2J+rVF/jvq4G85+mAWY7pFXnaf4N08JPW3hgFjK/ulPT9kU/+eCHvbO03RDkeUppSiGQLxgDzCYq19TroREkWmPccYYVS3lPwLZMtzMxiyJbKpV3QmQIGRucb9c0FF+p/7vT98ebOkq+8a5XbsTSg9jO5Y+i4lmHm+Dr8J+PeY4DS6+JzT7f4Tv3C6f5J63seryXwqRggsj6aVBIUAu0bgsK0hbWtVApo3Jisx/0IaJBvRrYD2E5tBQwKxDR6YW0ZnKrMCjhSTRl9FUyQltf0vexGjaKhkgDrZQvtR6MSc/0007VFw==",
                  "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-010a507c1833636cd94bdb98bd93083a.pem",
                  "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:996229b2-8a4d-4801-9709-319c9c68dcd8",
                  "MessageAttributes" : {
                    "eventType" : {"Type":"String","Value":"OFFENDER_MOVEMENT-RECEPTION"},
                    "id" : {"Type":"String","Value":"fe7c603f-4549-324e-a1bf-b6addcb87edd"},
                    "contentType" : {"Type":"String","Value":"text/plain;charset=UTF-8"},
                    "timestamp" : {"Type":"Number.java.lang.Long","Value":"1623159717906"},
                    "publishedAt" : {"Type":"String","Value":"%s"}
                  }
                }
                """,
          fortyFiveMinutesAgo
        ),
        message
      )
      verify(eventsEmitter).convertAndSendWhenSignificant(
        offenderEventArgumentCaptor.capture()
      )
    }
  }

  @Nested
  internal inner class MessageYoungerThanFortyFiveMinutes {
    private var messageBody: String? = null
    private val prisonEventQueueSqsClient = mock<AmazonSQSAsync>()
    private val prisonEventQueueSqsDlqClient = mock<AmazonSQSAsync>()

    @BeforeEach
    @SneakyThrows
    fun setUp() {
      Mockito.`when`(message.queueUrl).thenReturn("https://aws.queue/my-queue")
      whenever(hmppsQueueService.findByQueueId("prisoneventqueue")).thenReturn(
        HmppsQueue(
          "prisoneventqueue", prisonEventQueueSqsClient, "prison-event-queue",
          prisonEventQueueSqsDlqClient, "prison-event-dlq"
        )
      )

      val fortyFourMinutesAgo = OffsetDateTime
        .now()
        .minusMinutes(44)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
      messageBody = String.format(
        """
                {
                  "Type" : "Notification",
                  "MessageId" : "670250d8-9806-5670-bdbb-09e1ede37855",
                  "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
                  "Message" : "{\"eventType\":\"OFFENDER_MOVEMENT-RECEPTION\",\"eventDatetime\":\"2021-06-08T14:41:11.526762\",\"offenderIdDisplay\":\"A5194DY\",\"bookingId\":1201234,\"movementSeq\":11,\"nomisEventType\":\"OFF_RECEP_OASYS\"}",
                  "Timestamp" : "2021-06-08T13:41:57.913Z",
                  "SignatureVersion" : "1",
                  "Signature" : "TcHxCUITm+AeySkOmm2bFYuubdfhIl4S0cmw2J+rVF/jvq4G85+mAWY7pFXnaf4N08JPW3hgFjK/ulPT9kU/+eCHvbO03RDkeUppSiGQLxgDzCYq19TroREkWmPccYYVS3lPwLZMtzMxiyJbKpV3QmQIGRucb9c0FF+p/7vT98ebOkq+8a5XbsTSg9jO5Y+i4lmHm+Dr8J+PeY4DS6+JzT7f4Tv3C6f5J63seryXwqRggsj6aVBIUAu0bgsK0hbWtVApo3Jisx/0IaJBvRrYD2E5tBQwKxDR6YW0ZnKrMCjhSTRl9FUyQltf0vexGjaKhkgDrZQvtR6MSc/0007VFw==",
                  "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-010a507c1833636cd94bdb98bd93083a.pem",
                  "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:996229b2-8a4d-4801-9709-319c9c68dcd8",
                  "MessageAttributes" : {
                    "eventType" : {"Type":"String","Value":"OFFENDER_MOVEMENT-RECEPTION"},
                    "id" : {"Type":"String","Value":"fe7c603f-4549-324e-a1bf-b6addcb87edd"},
                    "contentType" : {"Type":"String","Value":"text/plain;charset=UTF-8"},
                    "timestamp" : {"Type":"Number.java.lang.Long","Value":"1623159717906"},
                    "publishedAt" : {"Type":"String","Value":"%s"}
                  }
                }
                """,
        fortyFourMinutesAgo
      )
      listener.onPrisonerEvent(messageBody, message)
    }

    @Test
    @DisplayName("will not process message")
    fun willNotProcessMessage() {
      Mockito.verifyNoInteractions(eventsEmitter)
    }

    @Test
    @DisplayName("will resend message")
    fun willResendMessage() {
      verify(prisonEventQueueSqsClient).sendMessage(sendMessageRequestArgumentCaptor.capture())
    }

    @Test
    @DisplayName("will set visibility on message to 15 minutes")
    fun willSetVisibilityOnMessageTo15Minutes() {
      verify(prisonEventQueueSqsClient).sendMessage(sendMessageRequestArgumentCaptor.capture())
      assertThat(sendMessageRequestArgumentCaptor.value.delaySeconds).isEqualTo(15 * 60)
    }

    @Test
    @DisplayName("will resend message back to queue it came from")
    fun willResendMessageBackToQueueItCameFrom() {
      verify(prisonEventQueueSqsClient).sendMessage(sendMessageRequestArgumentCaptor.capture())
      assertThat(sendMessageRequestArgumentCaptor.value.queueUrl).isEqualTo("https://aws.queue/my-queue")
    }

    @Test
    @DisplayName("message will be sent untouched")
    fun messageWillBeSentUntouched() {
      verify(prisonEventQueueSqsClient).sendMessage(sendMessageRequestArgumentCaptor.capture())
      assertThat(sendMessageRequestArgumentCaptor.value.messageBody).isEqualTo(messageBody)
    }
  }
}
