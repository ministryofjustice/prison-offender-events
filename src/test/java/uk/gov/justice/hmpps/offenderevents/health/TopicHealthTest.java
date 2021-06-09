package uk.gov.justice.hmpps.offenderevents.health;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.GetTopicAttributesResult;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopicHealthTest {
    private AmazonSNS awsSnsClient = mock(AmazonSNS.class);
    private String arn = "SOME_ARN";
    private TopicHealth topicHealth = new TopicHealth(awsSnsClient, arn);

    @Test
    @DisplayName("Topic health is up")
    void topicHealthIsUp() {
        when(awsSnsClient.getTopicAttributes("SOME_ARN")).thenReturn(new GetTopicAttributesResult());

        val health = topicHealth.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("arn", "SOME_ARN");
    }

    @Test
    @DisplayName("Topic health is down")
    void topicHealthIsDown() {
        when(awsSnsClient.getTopicAttributes("SOME_ARN")).thenThrow(new RuntimeException());

        val health = topicHealth.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("arn", "SOME_ARN");

    }
}
