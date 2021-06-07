package uk.gov.justice.hmpps.offenderevents.subscribe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PrisonerEventsListener {
    private final ObjectMapper objectMapper;

    public PrisonerEventsListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @JmsListener(destination = "${sqs.queue.name}")
    public void onPrisonerEvent(String message) throws JsonProcessingException {
        final var sqsMessage = objectMapper.readValue(message, SQSMessage.class);
        log.info("Received message {}", sqsMessage.MessageId());
    }
}

record SQSMessage(String Message, String MessageId) {
}
