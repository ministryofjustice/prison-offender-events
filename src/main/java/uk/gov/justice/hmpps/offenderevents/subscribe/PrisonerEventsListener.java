package uk.gov.justice.hmpps.offenderevents.subscribe;

import com.amazon.sqs.javamessaging.message.SQSTextMessage;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;
import uk.gov.justice.hmpps.offenderevents.services.HMPPSDomainEventsEmitter;
import uk.gov.justice.hmpps.sqs.HmppsQueueService;

import java.time.Duration;
import java.time.OffsetDateTime;

@Service
@Slf4j
public class PrisonerEventsListener {
    private final ObjectMapper objectMapper;
    private final HMPPSDomainEventsEmitter eventsEmitter;
    private final HmppsQueueService hmppsQueueService;
    private final Duration totalDelay;
    private final Duration delay;

    public PrisonerEventsListener(ObjectMapper objectMapper,
                                  HMPPSDomainEventsEmitter eventsEmitter,
                                  HmppsQueueService hmppsQueueService,
                                  @Value("${application.listener.totalDelayDuration}") Duration totalDelay,
                                  @Value("${application.listener.delayDuration}") Duration delay) {
        this.objectMapper = objectMapper;
        this.eventsEmitter = eventsEmitter;
        this.hmppsQueueService = hmppsQueueService;
        this.totalDelay = totalDelay;
        this.delay = delay;
    }

    @JmsListener(destination = "prisoneventqueue", containerFactory = "hmppsQueueContainerFactoryProxy")
    public void onPrisonerEvent(String message, SQSTextMessage rawMessage) throws JsonProcessingException {
        final var sqsMessage = objectMapper.readValue(message, SQSMessage.class);
        final var publishedAt = OffsetDateTime.parse(sqsMessage.MessageAttributes().publishedAt().Value());
        if (publishedAt.isBefore(OffsetDateTime.now().minus(totalDelay))) {
            log.debug("Received message {} published at {}", sqsMessage.MessageId(), publishedAt);
            final var event = objectMapper.readValue(sqsMessage.Message(), OffenderEvent.class);
            eventsEmitter.convertAndSendWhenSignificant(event);
        } else {
            hmppsQueueService.findByQueueId("prisoneventqueue").getSqsClient()
                .sendMessage(new SendMessageRequest()
                    .withQueueUrl(rawMessage.getQueueUrl())
                    .withMessageBody(message)
                    .withDelaySeconds((int) delay.toSeconds()));
        }
    }

}

record SQSMessage(String Message, String MessageId, MessageAttributes MessageAttributes) {
}

record PublishedAt(String Value) {
}

record MessageAttributes(PublishedAt publishedAt) {
}
