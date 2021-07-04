package uk.gov.justice.hmpps.offenderevents.e2e;


import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import junit.framework.AssertionFailedError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.justice.hmpps.offenderevents.resource.QueueListenerIntegrationTest;
import uk.gov.justice.hmpps.offenderevents.services.wiremock.CommunityApiExtension;
import uk.gov.justice.hmpps.offenderevents.services.wiremock.HMPPSAuthExtension;
import uk.gov.justice.hmpps.offenderevents.services.wiremock.PrisonApiExtension;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith({PrisonApiExtension.class, CommunityApiExtension.class, HMPPSAuthExtension.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@ActiveProfiles({"integration-test"})
public class HMPPSDomainEventsTest extends QueueListenerIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        PrisonApiExtension.server.stubFirstPollWithOffenderEvents("""
            []
            """);

        purgeQueues();
    }


    private List<String> geMessagesCurrentlyOnQueue() {
        final var messageResult = awsSqsClient.receiveMessage(getQueueUrl());
        return messageResult
            .getMessages()
            .stream()
            .map(Message::getBody)
            .map(this::toSQSMessage)
            .map(SQSMessage::Message)
            .toList();
    }


    private List<String> geMessagesCurrentlyOnHMPPSQueue() {
        final var messageResult = awsHmppsSqsClient.receiveMessage(getHmppsQueueUrl());
        return messageResult
            .getMessages()
            .stream()
            .map(Message::getBody)
            .map(this::toSQSMessage)
            .map(SQSMessage::Message)
            .toList();
    }

    private SQSMessage toSQSMessage(String message) {
        try {
            return objectMapper.readValue(message, SQSMessage.class);
        } catch (JsonProcessingException e) {
            throw new AssertionFailedError(String.format("Message %s is not parseable", message));
        }
    }

    private void purgeQueues() {
        System.out.println("Purging queues");
        awsSqsClient.purgeQueue(new PurgeQueueRequest(getQueueUrl()));
        await().until(() -> getNumberOfMessagesCurrentlyOnQueue() == 0);
        awsHmppsSqsClient.purgeQueue(new PurgeQueueRequest(getHmppsQueueUrl()));
        await().until(() -> getNumberOfMessagesCurrentlyOnHMPPSQueue() == 0);
    }

    record SQSMessage(String Message) {
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
        class WhenIsReportedAsRecall {
            @BeforeEach
            void setUp() {
                PrisonApiExtension.server.stubPrisonerDetails("A5194DY", "RECALL", true, "ADM", "R", "ACTIVE IN", "MDI");
            }

            @Test
            @DisplayName("will publish prison event and hmpps domain event for reception")
            void willPublishPrisonEventForReception() {
                await().until(() -> getNumberOfMessagesCurrentlyOnQueue() == 1);
                await().until(() -> getNumberOfMessagesCurrentlyOnHMPPSQueue() == 1);
            }

            @Test
            @DisplayName("will publish OFFENDER_MOVEMENT-RECEPTION prison event")
            void willPublishPrisonEvent() {
                await().until(() -> getNumberOfMessagesCurrentlyOnQueue() == 1);
                final var prisonEventMessages = geMessagesCurrentlyOnQueue();
                assertThat(prisonEventMessages)
                    .singleElement()
                    .satisfies(event -> assertThatJson(event)
                        .node("eventType")
                        .isEqualTo("OFFENDER_MOVEMENT-RECEPTION"));
            }

            @Test
            @DisplayName("will publish prison-offender-events.prisoner.received HMPPS domain event without asking community-api")
            void willPublishHMPPSDomainEvent() {
                await().until(() -> getNumberOfMessagesCurrentlyOnHMPPSQueue() == 1);
                final var hmppsEventMessages = geMessagesCurrentlyOnHMPPSQueue();
                assertThat(hmppsEventMessages).singleElement().satisfies(event -> {
                    assertThatJson(event).node("eventType").isEqualTo("prison-offender-events.prisoner.received");
                    assertThatJson(event).node("additionalInformation.reason").isEqualTo("RECALL");
                    assertThatJson(event).node("additionalInformation.prisonId").isEqualTo("MDI");
                    assertThatJson(event).node("additionalInformation.source").isEqualTo("PRISON");
                    assertThatJson(event).node("additionalInformation.currentLocation").isEqualTo("IN_PRISON");
                    assertThatJson(event).node("additionalInformation.currentPrisonStatus").isEqualTo("UNDER_PRISON_CARE");
                });

                CommunityApiExtension.server.verify(0, getRequestedFor(anyUrl()));
            }
        }

        @Nested
        class WhenIsReportedAsSentenced {
            @BeforeEach
            void setUp() {
                PrisonApiExtension.server.stubPrisonerDetails("A5194DY", "SENTENCED", false, "ADM", "K", "ACTIVE IN", "MDI");
            }

            @Test
            @DisplayName("will publish prison-offender-events.prisoner.received HMPPS domain event by asking community-api")
            void willPublishHMPPSDomainEvent() {
                await().until(() -> getNumberOfMessagesCurrentlyOnHMPPSQueue() == 1);
                final var hmppsEventMessages = geMessagesCurrentlyOnHMPPSQueue();
                assertThat(hmppsEventMessages).singleElement().satisfies(event -> {
                    assertThatJson(event).node("eventType").isEqualTo("prison-offender-events.prisoner.received");
                    assertThatJson(event).node("additionalInformation.reason").isEqualTo("CONVICTED");
                    assertThatJson(event).node("additionalInformation.prisonId").isEqualTo("MDI");
                    assertThatJson(event).node("additionalInformation.source").isEqualTo("PRISON");
                });

                CommunityApiExtension.server.verify(getRequestedFor(WireMock.urlEqualTo("/secure/offenders/nomsNumber/A5194DY/convictions/active/nsis/recall")));
            }
            @Test
            @DisplayName("will publish a recalled  prison-offender-events.prisoner.received HMPPS domain event when community-api indicates a recall")
            void willPublishRecallHMPPSDomainEvent() {
                CommunityApiExtension.server.stubForRecall("A5194DY");

                await().until(() -> getNumberOfMessagesCurrentlyOnHMPPSQueue() == 1);
                final var hmppsEventMessages = geMessagesCurrentlyOnHMPPSQueue();
                assertThat(hmppsEventMessages).singleElement().satisfies(event -> {
                    assertThatJson(event).node("eventType").isEqualTo("prison-offender-events.prisoner.received");
                    assertThatJson(event).node("additionalInformation.reason").isEqualTo("RECALL");
                    assertThatJson(event).node("additionalInformation.prisonId").isEqualTo("MDI");
                    assertThatJson(event).node("additionalInformation.source").isEqualTo("PROBATION");
                });

                CommunityApiExtension.server.verify(getRequestedFor(WireMock.urlEqualTo("/secure/offenders/nomsNumber/A5194DY/convictions/active/nsis/recall")));
            }
        }
    }
    @Nested
    class ReleasePrisoner {
        @BeforeEach
        void setUp() {
            PrisonApiExtension.server.stubFirstPollWithOffenderEvents("""
                [
                    {
                        "eventType":"OFFENDER_MOVEMENT-DISCHARGE",
                        "eventDatetime":"2021-06-08T14:41:11.526762",
                        "offenderIdDisplay":"A5194DY",
                        "bookingId":1201234,
                        "movementSeq":11,
                        "nomisEventType":"OFF_DISCH_OASYS"
                    }
                ]
                """);

        }

        @Nested
        class WhenIsReportedAsTransfer {
            @BeforeEach
            void setUp() {
                PrisonApiExtension.server.stubPrisonerDetails("A5194DY", "RECALL", true, "TRN", "PROD", "INACTIVE TRN", "WWA");
            }

            @Test
            @DisplayName("will publish prison event and hmpps domain event for release transfer")
            void willPublishPrisonEventForTransferRelease() {
                await().until(() -> getNumberOfMessagesCurrentlyOnQueue() == 1);
                await().until(() -> getNumberOfMessagesCurrentlyOnHMPPSQueue() == 1);
            }

            @Test
            @DisplayName("will publish OFFENDER_MOVEMENT-DISCHARGE prison event")
            void willPublishPrisonEvent() {
                await().until(() -> getNumberOfMessagesCurrentlyOnQueue() == 1);
                final var prisonEventMessages = geMessagesCurrentlyOnQueue();
                assertThat(prisonEventMessages)
                    .singleElement()
                    .satisfies(event -> assertThatJson(event)
                        .node("eventType")
                        .isEqualTo("OFFENDER_MOVEMENT-DISCHARGE"));
            }

            @Test
            @DisplayName("will publish prison-offender-events.prisoner.released HMPPS domain event")
            void willPublishHMPPSDomainEvent() {
                await().until(() -> getNumberOfMessagesCurrentlyOnHMPPSQueue() == 1);
                final var hmppsEventMessages = geMessagesCurrentlyOnHMPPSQueue();
                assertThat(hmppsEventMessages).singleElement().satisfies(event -> {
                    assertThatJson(event).node("eventType").isEqualTo("prison-offender-events.prisoner.released");
                    assertThatJson(event).node("additionalInformation.reason").isEqualTo("TRANSFERRED");
                    assertThatJson(event).node("additionalInformation.prisonId").isEqualTo("WWA");
                    assertThatJson(event).node("additionalInformation.currentLocation").isEqualTo("BEING_TRANSFERRED");
                    assertThatJson(event).node("additionalInformation.currentPrisonStatus").isEqualTo("NOT_UNDER_PRISON_CARE");
                });

                CommunityApiExtension.server.verify(0, getRequestedFor(anyUrl()));
            }
        }

        @Nested
        class WhenIsReportedAsRelease {
            @BeforeEach
            void setUp() {
                PrisonApiExtension.server.stubPrisonerDetails("A5194DY", "SENTENCED", false, "REL", "CA", "INACTIVE OUT", "MDI");
            }

            @Test
            @DisplayName("will publish prison-offender-events.prisoner.release HMPPS domain event")
            void willPublishHMPPSDomainEvent() {
                await().until(() -> getNumberOfMessagesCurrentlyOnHMPPSQueue() == 1);
                final var hmppsEventMessages = geMessagesCurrentlyOnHMPPSQueue();
                assertThat(hmppsEventMessages).singleElement().satisfies(event -> {
                    assertThatJson(event).node("eventType").isEqualTo("prison-offender-events.prisoner.released");
                    assertThatJson(event).node("additionalInformation.reason").isEqualTo("RELEASED");
                    assertThatJson(event).node("additionalInformation.prisonId").isEqualTo("MDI");
                    assertThatJson(event).node("additionalInformation.currentLocation").isEqualTo("OUTSIDE_PRISON");
                    assertThatJson(event).node("additionalInformation.currentPrisonStatus").isEqualTo("NOT_UNDER_PRISON_CARE");
                });
            }
        }
    }
}

