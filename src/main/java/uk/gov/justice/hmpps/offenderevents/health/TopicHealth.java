package uk.gov.justice.hmpps.offenderevents.health;

import com.amazonaws.services.sns.AmazonSNS;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import uk.gov.justice.hmpps.offenderevents.config.SqsConfigProperties;

import static uk.gov.justice.hmpps.offenderevents.config.SqsConfigPropertiesKt.prisonEventTopic;

@Component
@Slf4j
public class TopicHealth implements HealthIndicator {
    private final AmazonSNS awsPrisonEventsSnsClient;
    private final String arn;
   // private final String hmppsArn;

    public TopicHealth(@Qualifier("awsPrisonEventsSnsClient") AmazonSNS awsPrisonEventsSnsClient, SqsConfigProperties sqsConfigProperties) {
        this.awsPrisonEventsSnsClient = awsPrisonEventsSnsClient;
        this.arn = prisonEventTopic(sqsConfigProperties).getTopicArn();
       // this.hmppsArn = sqsConfigProperties.getQueues().get("hmppsDomainEventQueue").getTopicArn();
    }

    public Health health() {
        try {
            awsPrisonEventsSnsClient.getTopicAttributes(arn);
            return new Health.Builder().up().withDetail("arn", arn).build();
        } catch (Exception ex) {
            log.error("Health failed for SNS Topic due to ", ex);
            return new Health.Builder().down(ex).withDetail("arn", arn).build();
        }
    }
}
