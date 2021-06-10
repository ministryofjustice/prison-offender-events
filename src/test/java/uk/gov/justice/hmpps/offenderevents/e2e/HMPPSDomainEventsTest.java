package uk.gov.justice.hmpps.offenderevents.e2e;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.justice.hmpps.offenderevents.services.wiremock.CommunityApiExtension;
import uk.gov.justice.hmpps.offenderevents.services.wiremock.HMPPSAuthExtension;
import uk.gov.justice.hmpps.offenderevents.services.wiremock.PrisonApiExtension;

import java.util.Arrays;
import java.util.List;

import static org.awaitility.Awaitility.await;

@ExtendWith({PrisonApiExtension.class, CommunityApiExtension.class, HMPPSAuthExtension.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"integration-test"})
public class HMPPSDomainEventsTest {
    private static final String PRISON_EVENTS_SUBSCRIBE_QUEUE_NAME = "test-prison-event_queue";
    private static final String HMPPS_DOMAIN_EVENTS_SUBSCRIBE_QUEUE_NAME = "test-hmpps-domain-event_queue";
    @Autowired
    private AmazonSQS awsSqsClient;

    @BeforeEach
    void setUp() {
        PrisonApiExtension.server.stubFirstPollWithOffenderEvents("""
            []
            """);

        purgeQueues(PRISON_EVENTS_SUBSCRIBE_QUEUE_NAME, HMPPS_DOMAIN_EVENTS_SUBSCRIBE_QUEUE_NAME);
    }

    private int getNumberOfMessagesCurrentlyOnQueue(String queueName) {
        final var queueUrl = awsSqsClient.getQueueUrl(queueName).getQueueUrl();
        final var queueAttributes = awsSqsClient.getQueueAttributes(queueUrl, List.of("ApproximateNumberOfMessages"));
        return Integer.parseInt(queueAttributes.getAttributes().get("ApproximateNumberOfMessages"));
    }

    private void purgeQueues(String... queueNames) {
        Arrays.asList(queueNames).forEach(queueName -> {
            final var queueUrl = awsSqsClient.getQueueUrl(queueName).getQueueUrl();
            awsSqsClient.purgeQueue(new PurgeQueueRequest(queueUrl));
        });
    }

    @Nested
    class ReceivePrisoner {
        @BeforeEach
        void setUp() {
            PrisonApiExtension.server.stubFirstPollWithOffenderEvents("""
                [
                    {
                        "eventType":"OFFENDER_MOVEMENT-RECEPTION",
                        "eventDatetime":"2021-06-08T14:41:11.526762",
                        "offenderIdDisplay":"A5194DY",
                        "bookingId":1201234,
                        "movementSeq":11,
                        "nomisEventType":"OFF_RECEP_OASYS"
                    }
                ]
                """);

        }

        @Nested
        class WhenIsReportedAsSentenced {
            @BeforeEach
            void setUp() {
                PrisonApiExtension.server.stubPrisonerDetails("A5194DY", "SENTENCED", false, "ADM");
            }

            @Test
            @DisplayName("will publish received message using information from prison api and community api")
            void willPublishReceivedMessageUsingInformationFromPrisonApiAndCommunityApi() {
                await().until(() -> getNumberOfMessagesCurrentlyOnQueue(PRISON_EVENTS_SUBSCRIBE_QUEUE_NAME) == 1);
                await().until(() -> getNumberOfMessagesCurrentlyOnQueue(HMPPS_DOMAIN_EVENTS_SUBSCRIBE_QUEUE_NAME) == 1);

                // TODO assert on message and prison-api and community-api call
            }
        }
        @Nested
        class WhenIsReportedAsRecall {
            @BeforeEach
            void setUp() {
                PrisonApiExtension.server.stubPrisonerDetails("A5194DY", "RECALL", true, "ADM");
            }

            @Test
            @DisplayName("will publish received message using information from prison api and community api")
            void willPublishReceivedMessageUsingInformationFromPrisonApiAndCommunityApi() {
                await().until(() -> getNumberOfMessagesCurrentlyOnQueue(PRISON_EVENTS_SUBSCRIBE_QUEUE_NAME) == 1);
                await().until(() -> getNumberOfMessagesCurrentlyOnQueue(HMPPS_DOMAIN_EVENTS_SUBSCRIBE_QUEUE_NAME) == 1);

                // TODO assert on message and prison-api and community-api call
            }
        }
    }
}
