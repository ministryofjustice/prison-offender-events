package uk.gov.justice.hmpps.offenderevents.health;

import com.amazonaws.services.sns.AmazonSNS;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TopicHealth implements HealthIndicator {
    private final AmazonSNS awsSnsClient;
    private final String arn;

    public TopicHealth(AmazonSNS awsSnsClient, @Value("${sns.topic.arn}") String arn) {
        this.awsSnsClient = awsSnsClient;
        this.arn = arn;
    }

    public Health health() {
        try {
            awsSnsClient.getTopicAttributes(arn);
            return new Health.Builder().up().withDetail("arn", arn).build();
        } catch (Exception ex) {
            log.error("Health failed for SNS Topic due to ", ex);
            return new Health.Builder().down(ex).withDetail("arn", arn).build();
        }
    }
}
