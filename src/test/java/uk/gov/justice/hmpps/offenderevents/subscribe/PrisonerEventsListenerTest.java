package uk.gov.justice.hmpps.offenderevents.subscribe;

import com.amazon.sqs.javamessaging.message.SQSTextMessage;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;
import uk.gov.justice.hmpps.offenderevents.services.HMPPSDomainEventsEmitter;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@JsonTest
class PrisonerEventsListenerTest {
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HMPPSDomainEventsEmitter eventsEmitter;

    @MockBean
    private AmazonSQS client;

    @Captor
    private ArgumentCaptor<OffenderEvent> offenderEventArgumentCaptor;
    @Captor
    private ArgumentCaptor<SendMessageRequest> sendMessageRequestArgumentCaptor;

    private PrisonerEventsListener listener;
    @MockBean
    private SQSTextMessage message;

    @BeforeEach
    void setUp() {
        listener = new PrisonerEventsListener(objectMapper, eventsEmitter, client, Duration.ofMinutes(45), Duration.ofMinutes(15));
    }

    @Nested
    class MessageOlderThanFortyFiveMinutes {
        @SneakyThrows
        @Test
        @DisplayName("Will pass offender event to events emitter")
        void willPassOffenderEventToEventsEmitter() {
            listener.onPrisonerEvent("""
                {
                  "Type" : "Notification",
                  "MessageId" : "670250d8-9806-5670-bdbb-09e1ede37855",
                  "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
                  "Message" : "{\\"eventType\\":\\"OFFENDER_MOVEMENT-RECEPTION\\",\\"eventDatetime\\":\\"2021-06-08T14:41:11.526762\\",\\"offenderIdDisplay\\":\\"A5194DY\\",\\"bookingId\\":1201234,\\"movementSeq\\":11,\\"nomisEventType\\":\\"OFF_RECEP_OASYS\\"}",
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
                """, message);

            verify(eventsEmitter).convertAndSendWhenSignificant(offenderEventArgumentCaptor.capture());

            assertThat(offenderEventArgumentCaptor.getValue().getEventType()).isEqualTo("OFFENDER_MOVEMENT-RECEPTION");
            assertThat(offenderEventArgumentCaptor.getValue().getOffenderIdDisplay()).isEqualTo("A5194DY");
        }

        @SneakyThrows
        @Test
        @DisplayName("will process message if published more than 45 minutes ago")
        void willProcessMessageIfPublishedMoreThan45MinutesAgo() {
            final var fortyFiveMinutesAgo = OffsetDateTime
                .now()
                .minusMinutes(45)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            listener.onPrisonerEvent(String.format("""
                {
                  "Type" : "Notification",
                  "MessageId" : "670250d8-9806-5670-bdbb-09e1ede37855",
                  "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
                  "Message" : "{\\"eventType\\":\\"OFFENDER_MOVEMENT-RECEPTION\\",\\"eventDatetime\\":\\"2021-06-08T14:41:11.526762\\",\\"offenderIdDisplay\\":\\"A5194DY\\",\\"bookingId\\":1201234,\\"movementSeq\\":11,\\"nomisEventType\\":\\"OFF_RECEP_OASYS\\"}",
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
                """, fortyFiveMinutesAgo), message);
            verify(eventsEmitter).convertAndSendWhenSignificant(offenderEventArgumentCaptor.capture());
        }

    }
    @Nested
    class MessageYoungerThanFortyFiveMinutes {
        private String messageBody;
        @BeforeEach
        @SneakyThrows
        void setUp() {
            when(message.getQueueUrl()).thenReturn("https://aws.queue/my-queue");
            final var fortyFourMinutesAgo = OffsetDateTime
                .now()
                .minusMinutes(44)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            messageBody = String.format("""
                {
                  "Type" : "Notification",
                  "MessageId" : "670250d8-9806-5670-bdbb-09e1ede37855",
                  "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
                  "Message" : "{\\"eventType\\":\\"OFFENDER_MOVEMENT-RECEPTION\\",\\"eventDatetime\\":\\"2021-06-08T14:41:11.526762\\",\\"offenderIdDisplay\\":\\"A5194DY\\",\\"bookingId\\":1201234,\\"movementSeq\\":11,\\"nomisEventType\\":\\"OFF_RECEP_OASYS\\"}",
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
                """, fortyFourMinutesAgo);
            listener.onPrisonerEvent(messageBody, message);

        }

        @Test
        @DisplayName("will not process message")
        void willNotProcessMessage() {
            verifyNoInteractions(eventsEmitter);
        }

        @Test
        @DisplayName("will resend message")
        void willResendMessage() {
          verify(client).sendMessage(sendMessageRequestArgumentCaptor.capture());
        }

        @Test
        @DisplayName("will set visibility on message to 15 minutes")
        void willSetVisibilityOnMessageTo15Minutes() {
            verify(client).sendMessage(sendMessageRequestArgumentCaptor.capture());
            assertThat(sendMessageRequestArgumentCaptor.getValue().getDelaySeconds()).isEqualTo(15 * 60);
        }

        @Test
        @DisplayName("will resend message back to queue it came from")
        void willResendMessageBackToQueueItCameFrom() {
            verify(client).sendMessage(sendMessageRequestArgumentCaptor.capture());
            assertThat(sendMessageRequestArgumentCaptor.getValue().getQueueUrl()).isEqualTo("https://aws.queue/my-queue");
        }

        @Test
        @DisplayName("message will be sent untouched")
        void messageWillBeSentUntouched() {
            verify(client).sendMessage(sendMessageRequestArgumentCaptor.capture());
            assertThat(sendMessageRequestArgumentCaptor.getValue().getMessageBody()).isEqualTo(messageBody);
        }
    }
}
