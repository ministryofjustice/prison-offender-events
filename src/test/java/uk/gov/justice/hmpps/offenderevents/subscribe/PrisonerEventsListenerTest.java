package uk.gov.justice.hmpps.offenderevents.subscribe;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;
import uk.gov.justice.hmpps.offenderevents.services.HMPPSDomainEventsEmitter;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.verify;

@JsonTest
class PrisonerEventsListenerTest {
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HMPPSDomainEventsEmitter eventsEmitter;

    @Captor
    private ArgumentCaptor<OffenderEvent> offenderEventArgumentCaptor;

    private PrisonerEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new PrisonerEventsListener(objectMapper, eventsEmitter);
    }

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
                "timestamp" : {"Type":"Number.java.lang.Long","Value":"1623159717906"}
              }
            }
            """);

        verify(eventsEmitter).convertAndSendWhenSignificant(offenderEventArgumentCaptor.capture());

        assertThat(offenderEventArgumentCaptor.getValue().getEventType()).isEqualTo("OFFENDER_MOVEMENT-RECEPTION");
        assertThat(offenderEventArgumentCaptor.getValue().getOffenderIdDisplay()).isEqualTo("A5194DY");
    }

}
