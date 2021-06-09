package uk.gov.justice.hmpps.offenderevents.health;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.GetQueueUrlResult;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Health.Builder;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.amazonaws.services.sqs.model.QueueAttributeName.All;
import static com.amazonaws.services.sqs.model.QueueAttributeName.ApproximateNumberOfMessages;
import static com.amazonaws.services.sqs.model.QueueAttributeName.ApproximateNumberOfMessagesNotVisible;
import static uk.gov.justice.hmpps.offenderevents.health.DlqStatus.NOT_ATTACHED;
import static uk.gov.justice.hmpps.offenderevents.health.DlqStatus.NOT_AVAILABLE;
import static uk.gov.justice.hmpps.offenderevents.health.DlqStatus.NOT_FOUND;
import static uk.gov.justice.hmpps.offenderevents.health.DlqStatus.UP;
import static uk.gov.justice.hmpps.offenderevents.health.QueueAttributes.MESSAGES_IN_FLIGHT;
import static uk.gov.justice.hmpps.offenderevents.health.QueueAttributes.MESSAGES_ON_DLQ;
import static uk.gov.justice.hmpps.offenderevents.health.QueueAttributes.MESSAGES_ON_QUEUE;

@Getter
enum DlqStatus {

    UP("UP"),
    NOT_ATTACHED("The queue does not have a dead letter queue attached"),
    NOT_FOUND("The queue does not exist"),
    NOT_AVAILABLE("The queue cannot be interrogated");

    private final String description;

    DlqStatus(String description) {
        this.description = description;
    }
}


@Getter
enum QueueAttributes {
    MESSAGES_ON_QUEUE(ApproximateNumberOfMessages.toString(), "MessagesOnQueue"),
    MESSAGES_IN_FLIGHT(ApproximateNumberOfMessagesNotVisible.toString(), "MessagesInFlight"),
    MESSAGES_ON_DLQ(ApproximateNumberOfMessages.toString(), "MessagesOnDLQ");

    private final String awsName;
    private final String healthName;

    QueueAttributes(String awsName, String healthName) {
        this.awsName = awsName;
        this.healthName = healthName;
    }
}

@Slf4j
abstract class QueueHealth implements HealthIndicator {
    private final AmazonSQS awsSqsClient;
    private final AmazonSQS awsSqsDlqClient;
    private final String queueName;
    private final String dlqName;

    public QueueHealth(AmazonSQS awsSqsClient, AmazonSQS awsSqsDlqClient, String queueName, String dlqName) {
        this.awsSqsClient = awsSqsClient;
        this.awsSqsDlqClient = awsSqsDlqClient;
        this.queueName = queueName;
        this.dlqName = dlqName;
    }

    public Health health() {
        try {
            final var url = awsSqsClient.getQueueUrl(queueName);
            final var queueAttributes = awsSqsClient.getQueueAttributes(getQueueAttributesRequest(url));
            final var details = Map.of(
                MESSAGES_ON_QUEUE.getHealthName(), queueAttributes.getAttributes().get(MESSAGES_ON_QUEUE.getAwsName()),
                MESSAGES_IN_FLIGHT.getHealthName(), queueAttributes
                    .getAttributes()
                    .get(MESSAGES_IN_FLIGHT.getAwsName()));

            return addDlqHealth(new Builder().up().withDetails(details), queueAttributes).build();
        } catch (Exception e) {
            log.error("Unable to retrieve queue attributes for queue '{}' due to exception:", queueName, e);
            return new Builder().down().withException(e).build();
        }

    }

    private Builder addDlqHealth(Builder builder, GetQueueAttributesResult mainQueueAttributes) {
        if (!mainQueueAttributes.getAttributes().containsKey("RedrivePolicy")) {
            log.error(
                "Queue '{}' is missing a RedrivePolicy attribute indicating it does not have a dead letter queue",
                queueName
            );
            return builder.down().withDetail("dlqStatus", NOT_ATTACHED.getDescription());
        }

        try {
            final var url = awsSqsDlqClient.getQueueUrl(dlqName);
            final var dlqAttributes = awsSqsDlqClient.getQueueAttributes(getQueueAttributesRequest(url));
            return builder.up().withDetail("dlqStatus", UP.getDescription())
                .withDetail(MESSAGES_ON_DLQ.getHealthName(), dlqAttributes
                    .getAttributes()
                    .get(MESSAGES_ON_DLQ.getAwsName()));
        } catch (QueueDoesNotExistException e) {
            log.error("Unable to retrieve dead letter queue URL for queue '{}' due to exception:", queueName, e);
            return builder.down(e).withDetail("dlqStatus", NOT_FOUND.getDescription());
        } catch (Exception e) {
            log.error("Unable to retrieve dead letter queue attributes for queue '{}' due to exception:", queueName, e);
            return builder.down(e).withDetail("dlqStatus", NOT_AVAILABLE.getDescription());
        }

    }

    private GetQueueAttributesRequest getQueueAttributesRequest(GetQueueUrlResult url) {
        return new GetQueueAttributesRequest(url.getQueueUrl()).withAttributeNames(All);
    }
}

@Component
public class PrisonEventsQueueHealth extends QueueHealth {
    public PrisonEventsQueueHealth(@Qualifier("awsSqsClient") AmazonSQS awsSqsClient,
                                   @Qualifier("awsSqsDlqClient") AmazonSQS awsSqsDlqClient,
                                   @Value("${sqs.queue.name}") String queueName,
                                   @Value("${sqs.dlq.name}") String dlqName) {
        super(awsSqsClient, awsSqsDlqClient, queueName, dlqName);
    }
}
