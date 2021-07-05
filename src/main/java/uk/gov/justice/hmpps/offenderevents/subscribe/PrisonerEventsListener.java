package uk.gov.justice.hmpps.offenderevents.subscribe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;
import uk.gov.justice.hmpps.offenderevents.services.HMPPSDomainEventsEmitter;

@Service
@Slf4j
public class PrisonerEventsListener {
    private final ObjectMapper objectMapper;
    private final HMPPSDomainEventsEmitter eventsEmitter;

    public PrisonerEventsListener(ObjectMapper objectMapper, HMPPSDomainEventsEmitter eventsEmitter) {
        this.objectMapper = objectMapper;
        this.eventsEmitter = eventsEmitter;
    }

  @JmsListener(destination = "#{@'hmpps.sqs-uk.gov.justice.hmpps.offenderevents.config.SqsConfigProperties'.queues['prisonEventQueue'].queueName}",
      containerFactory = "jmsListenerContainerFactory")
  public void onPrisonerEvent(String message) throws JsonProcessingException {
        final var sqsMessage = objectMapper.readValue(message, SQSMessage.class);
        log.debug("Received message {}", sqsMessage.MessageId());
        final var event = objectMapper.readValue(sqsMessage.Message(), OffenderEvent.class);
        eventsEmitter.convertAndSendWhenSignificant(event);
    }
}

record SQSMessage(String Message, String MessageId) {
}
