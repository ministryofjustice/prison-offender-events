package uk.gov.justice.hmpps.offenderevents.services;

import com.amazonaws.services.sns.AmazonSNSAsync;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.aws.messaging.core.NotificationMessagingTemplate;
import org.springframework.cloud.aws.messaging.core.TopicMessageChannel;
import org.springframework.stereotype.Service;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;

@Service
public class SnsService {

    private final NotificationMessagingTemplate topicTemplate;
    private final AmazonSNSAsync amazonSns;
    private final String topicArn;

    public SnsService(@Qualifier("awsSnsClient") AmazonSNSAsync amazonSns,
                      @Value("${sns.topic.arn}") String topicArn) {

        this.topicTemplate = new NotificationMessagingTemplate(amazonSns);
        this.topicArn = topicArn;
        this.amazonSns = amazonSns;
    }

    public void sendEvent(final OffenderEvent payload) {
        topicTemplate.convertAndSend(new TopicMessageChannel(amazonSns, topicArn), payload);
    }
}
