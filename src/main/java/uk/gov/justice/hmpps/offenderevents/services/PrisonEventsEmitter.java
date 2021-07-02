package uk.gov.justice.hmpps.offenderevents.services;

import com.amazonaws.services.sns.AmazonSNSAsync;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.aws.messaging.core.NotificationMessagingTemplate;
import org.springframework.cloud.aws.messaging.core.TopicMessageChannel;
import org.springframework.stereotype.Service;
import uk.gov.justice.hmpps.offenderevents.config.SqsConfigProperties;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import static uk.gov.justice.hmpps.offenderevents.config.SqsConfigPropertiesKt.prisonEventQueue;

@Service
@Slf4j
public class PrisonEventsEmitter {

    private final NotificationMessagingTemplate topicTemplate;
    private final AmazonSNSAsync awsPrisonEventsSnsClient;
    private final String topicArn;
    private final ObjectMapper objectMapper;
    private final TelemetryClient telemetryClient;


    public PrisonEventsEmitter(@Qualifier("awsPrisonEventsSnsClient") final AmazonSNSAsync awsPrisonEventsSnsClient,
                               final SqsConfigProperties sqsConfigProperties,
                               final ObjectMapper objectMapper,
                               final TelemetryClient telemetryClient) {

        this.topicTemplate = new NotificationMessagingTemplate(awsPrisonEventsSnsClient);
        this.topicArn = prisonEventQueue(sqsConfigProperties).getTopicArn();
        System.out.println("Prison Event Queue" + prisonEventQueue(sqsConfigProperties).getQueueName());
        this.awsPrisonEventsSnsClient = awsPrisonEventsSnsClient;
        this.objectMapper = objectMapper;
        this.telemetryClient = telemetryClient;
    }

    public void sendEvent(final OffenderEvent payload) {
        try {
            final var code = buildOptionalCode(payload);
            System.out.println("*****PrisonEmitter - > Sending Event " + payload.getEventType() +
                " topic:" + topicArn + " " + payload);
            System.out.println(awsPrisonEventsSnsClient.getTopicAttributes(topicArn));
            topicTemplate.convertAndSend(
                    new TopicMessageChannel(awsPrisonEventsSnsClient, topicArn),
                    objectMapper.writeValueAsString(payload),
                    code == null
                            ? Map.of("eventType", payload.getEventType())
                            : Map.of("eventType", payload.getEventType(), "code", code)
            );
            telemetryClient.trackEvent(payload.getEventType(), asTelemetryMap(payload), null);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert payload {} to json", payload);
        }
    }

    private Map<String, String> asTelemetryMap(OffenderEvent event) {
        final Set<Entry<String, Object>> entries = objectMapper
            .convertValue(event, new TypeReference<Map<String, Object>>() {
            })
            .entrySet();
        return entries.stream().collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().toString()));
    }

    private String buildOptionalCode(final OffenderEvent payload) {
        if (payload.getAlertCode() != null) {
            return payload.getAlertCode();
        }
        if (payload.getMovementType() != null) {
            return payload.getMovementType() + "-" + payload.getDirectionCode();
        }
        return null;
    }
}
