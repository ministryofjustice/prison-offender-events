package uk.gov.justice.hmpps.offenderevents.health;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.GetQueueUrlResult;
import com.amazonaws.services.sqs.model.QueueAttributeName;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.justice.hmpps.offenderevents.health.QueueAttributes.MESSAGES_IN_FLIGHT;
import static uk.gov.justice.hmpps.offenderevents.health.QueueAttributes.MESSAGES_ON_DLQ;
import static uk.gov.justice.hmpps.offenderevents.health.QueueAttributes.MESSAGES_ON_QUEUE;

class PrisonEventsQueueHealthTest {
    private final String someQueueName = "some queue name";
    private final String someQueueUrl = "some queue url";
    private final String someDLQName = "some DLQ name";
    private final String someDLQUrl = "some DLQ url";
    private final String someMessagesOnQueueCount = "123";
    private final String someMessagesInFlightCount = "456";
    private final String someMessagesOnDLQCount = "789";
    private final AmazonSQS amazonSqs = mock(AmazonSQS.class);
    private final AmazonSQS amazonSqsDLQ = mock(AmazonSQS.class);
    private final QueueHealth queueHealth = new PrisonEventsQueueHealth(amazonSqs, amazonSqsDLQ, someQueueName, someDLQName);


    @Test
    @DisplayName("health - queue found - UP")
    void healthQueueFoundUP() {
        mockHealthyQueue();

        final var health = queueHealth.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("health - attributes returned - included in health status")
    void healthAttributesReturnedIncludedInHealthStatus() {
        mockHealthyQueue();

        final var health = queueHealth.health();

        assertThat(health.getDetails().get(MESSAGES_ON_QUEUE.getHealthName())).isEqualTo(someMessagesOnQueueCount);
        assertThat(health.getDetails().get(MESSAGES_IN_FLIGHT.getHealthName())).isEqualTo(someMessagesInFlightCount);
    }

    @Test
    @DisplayName("health - queue not found - DOWN")
    void healthQueueNotFoundDOWN() {
        when(amazonSqs.getQueueUrl(anyString())).thenThrow(QueueDoesNotExistException.class);

        final var health = queueHealth.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("health - failed to get main queue attributes - DOWN")
    void healthFailedToGetMainQueueAttributesDOWN() {
        when(amazonSqs.getQueueUrl(anyString())).thenReturn(someGetQueueUrlResult());
        when(amazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenThrow(RuntimeException.class);

        final var health = queueHealth.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("health - DLQ UP - reports DLQ UP")
    void healthDLQUPReportsDLQUP() {
        mockHealthyQueue();

        final var health = queueHealth.health();

        assertThat(health.getDetails().get("dlqStatus")).isEqualTo(DlqStatus.UP.getDescription());
    }

    @Test
    @DisplayName("health - DLQ attributes returned - included in health status")
    void healthDLQAttributesReturnedIncludedInHealthStatus() {
        mockHealthyQueue();

        final var health = queueHealth.health();

        assertThat(health.getDetails().get(MESSAGES_ON_DLQ.getHealthName())).isEqualTo(someMessagesOnDLQCount);
    }

    @Test
    @DisplayName("health - DLQ down - main queue health also DOWN")
    void healthDLQDownMainQueueHealthAlsoDOWN() {
        when(amazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult());
        when(amazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(
            someGetQueueAttributesResultWithoutDLQ()
        );

        final var health = queueHealth.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("dlqStatus")).isEqualTo(DlqStatus.NOT_ATTACHED.getDescription());
    }

    @Test
    @DisplayName("health - no RedrivePolicy attribute on main queue - DLQ NOT ATTACHED")
    void healthNoRedrivePolicyAttributeOnMainQueueDLQNOTATTACHED() {

        when(amazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult());
        when(amazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(
            someGetQueueAttributesResultWithoutDLQ()
        );

        final var health = queueHealth.health();

        assertThat(health.getDetails().get("dlqStatus")).isEqualTo(DlqStatus.NOT_ATTACHED.getDescription());
    }

    @Test
    @DisplayName("health - DLQ not found - DLQ NOT FOUND")
    void healthDLQNotFoundDLQNOTFOUND() {
        when(amazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult());
        when(amazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(
            someGetQueueAttributesResultWithDLQ()
        );
        when(amazonSqsDLQ.getQueueUrl(someDLQName)).thenThrow(QueueDoesNotExistException.class);

        final var health = queueHealth.health();

        assertThat(health.getDetails().get("dlqStatus")).isEqualTo(DlqStatus.NOT_FOUND.getDescription());
    }

    @Test
    @DisplayName("health - DLQ failed to get attributes - DLQ NOT AVAILABLE")
    void healthDLQFailedToGetAttributesDLQNOTAVAILABLE() {
        when(amazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult());
        when(amazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(
            someGetQueueAttributesResultWithDLQ()
        );
        when(amazonSqsDLQ.getQueueUrl(someDLQName)).thenReturn(someGetQueueUrlResultForDLQ());
        when(amazonSqsDLQ.getQueueAttributes(someGetQueueAttributesRequestForDLQ())).thenThrow(RuntimeException.class);

        final var health = queueHealth.health();

        assertThat(health.getDetails().get("dlqStatus")).isEqualTo(DlqStatus.NOT_AVAILABLE.getDescription());
    }

    private void mockHealthyQueue() {
        when(amazonSqs.getQueueUrl(someQueueName)).thenReturn(someGetQueueUrlResult());
        when(amazonSqs.getQueueAttributes(someGetQueueAttributesRequest())).thenReturn(
            someGetQueueAttributesResultWithDLQ()
        );
        when(amazonSqsDLQ.getQueueUrl(someDLQName)).thenReturn(someGetQueueUrlResultForDLQ());
        when(amazonSqsDLQ.getQueueAttributes(someGetQueueAttributesRequestForDLQ())).thenReturn(
            someGetQueueAttributesResultForDLQ()
        );
    }

    private GetQueueAttributesRequest someGetQueueAttributesRequest() {
        return new GetQueueAttributesRequest(someQueueUrl).withAttributeNames(List.of(QueueAttributeName.All.toString()));
    }

    private GetQueueUrlResult someGetQueueUrlResult() {
        return new GetQueueUrlResult().withQueueUrl(someQueueUrl);
    }

    private GetQueueAttributesResult someGetQueueAttributesResultWithoutDLQ() {
        return new GetQueueAttributesResult().withAttributes(
            Map.of(
                MESSAGES_ON_QUEUE.getAwsName(), someMessagesOnQueueCount,
                MESSAGES_IN_FLIGHT.getAwsName(), someMessagesInFlightCount
            )
        );
    }

    private GetQueueAttributesResult someGetQueueAttributesResultWithDLQ() {
        return new GetQueueAttributesResult().withAttributes(
            Map.of(
                MESSAGES_ON_QUEUE.getAwsName(), someMessagesOnQueueCount,
                MESSAGES_IN_FLIGHT.getAwsName(), someMessagesInFlightCount,
                QueueAttributeName.RedrivePolicy.toString(), "any redrive policy"
            )
        );
    }

    private GetQueueAttributesRequest someGetQueueAttributesRequestForDLQ() {
        return new GetQueueAttributesRequest(someDLQUrl).withAttributeNames(List.of(QueueAttributeName.All.toString()));
    }


    private GetQueueUrlResult someGetQueueUrlResultForDLQ() {
        return new GetQueueUrlResult().withQueueUrl(someDLQUrl);
    }

    private GetQueueAttributesResult someGetQueueAttributesResultForDLQ() {
        return new GetQueueAttributesResult().withAttributes(
            Map.of(MESSAGES_ON_QUEUE.getAwsName(), someMessagesOnDLQCount)
        );
    }
}
