package uk.gov.justice.hmpps.offenderevents.services;

import com.amazonaws.services.sns.AmazonSNSAsync;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.aws.messaging.core.NotificationMessagingTemplate;
import org.springframework.cloud.aws.messaging.core.TopicMessageChannel;
import org.springframework.stereotype.Service;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;

import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class HMPPSDomainEventsEmitter {
    private final NotificationMessagingTemplate topicTemplate;
    private final AmazonSNSAsync amazonSns;
    private final String topicArn;
    private final ObjectMapper objectMapper;

    HMPPSDomainEventsEmitter(@Qualifier("awsHMPPSEventsSnsClient") final AmazonSNSAsync amazonSns,
                             @Value("${hmpps.sns.topic.arn}") final String topicArn,
                             final ObjectMapper objectMapper) {
        this.topicTemplate = new NotificationMessagingTemplate(amazonSns);
        this.topicArn = topicArn;
        this.amazonSns = amazonSns;
        this.objectMapper = objectMapper;

    }

    public void convertAndSendWhenSignificant(OffenderEvent event) {
        final Optional<HMPPSDomainEvent> hmppsEvent = switch (event.getEventType()) {
            case "OFFENDER_MOVEMENT-RECEPTION" -> Optional.of(toPrisonerReceived(event));
            case "OFFENDER_MOVEMENT-DISCHARGE" -> Optional.of(toPrisonerReleased(event));
            default -> Optional.empty();
        };

        hmppsEvent.ifPresent(this::sendEvent);
    }

    private HMPPSDomainEvent toPrisonerReceived(OffenderEvent event) {
        return new HMPPSDomainEvent("PRISONER_RECEIVED", event.getOffenderIdDisplay());
    }
    private HMPPSDomainEvent toPrisonerReleased(OffenderEvent event) {
        return new HMPPSDomainEvent("PRISONER_RELEASED", event.getOffenderIdDisplay());
    }

    public void sendEvent(final HMPPSDomainEvent payload) {
        try {
            topicTemplate.convertAndSend(
                new TopicMessageChannel(amazonSns, topicArn),
                objectMapper.writeValueAsString(payload),
                Map.of("eventType", payload.eventType())
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to convert payload {} to json", payload);
        }
    }
}

@JsonSerialize
record HMPPSDomainEvent(String eventType, String nomsNumber) {
}
