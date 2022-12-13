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
import java.util.List;

@Service
@Slf4j
public class PrisonerEventsListener {
    private final ObjectMapper objectMapper;
    private final HMPPSDomainEventsEmitter eventsEmitter;
    private final HmppsQueueService hmppsQueueService;
    private final Duration totalDelay;
    private final Duration delay;

    private static final List<String> DELAYED_EVENT_TYPES = List.of("OFFENDER_MOVEMENT-RECEPTION", "OFFENDER_MOVEMENT-DISCHARGE", "BOOKING_NUMBER-CHANGED");

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
        final var event = objectMapper.readValue(sqsMessage.Message(), OffenderEvent.class);
        if (!DELAYED_EVENT_TYPES.contains(event.getEventType()) || publishedAt.isBefore(OffsetDateTime.now().minus(totalDelay))) {
            log.debug("Received message {} type {} published at {}", sqsMessage.MessageId(), event.getEventType(), publishedAt);
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
