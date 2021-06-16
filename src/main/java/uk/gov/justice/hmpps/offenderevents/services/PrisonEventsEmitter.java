package uk.gov.justice.hmpps.offenderevents.services;

import com.amazonaws.services.sns.AmazonSNSAsync;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.aws.messaging.core.NotificationMessagingTemplate;
import org.springframework.cloud.aws.messaging.core.TopicMessageChannel;
import org.springframework.stereotype.Service;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PrisonEventsEmitter {

    private final NotificationMessagingTemplate topicTemplate;
    private final AmazonSNSAsync amazonSns;
    private final String topicArn;
    private final ObjectMapper objectMapper;
    private final TelemetryClient telemetryClient;


    public PrisonEventsEmitter(@Qualifier("awsPrisonEventsSnsClient") final AmazonSNSAsync amazonSns,
                               @Value("${sns.topic.arn}") final String topicArn,
                               final ObjectMapper objectMapper,
                               final TelemetryClient telemetryClient) {

        this.topicTemplate = new NotificationMessagingTemplate(amazonSns);
        this.topicArn = topicArn;
        this.amazonSns = amazonSns;
        this.objectMapper = objectMapper;
        this.telemetryClient = telemetryClient;
    }

    public void sendEvent(final OffenderEvent payload) {
        try {
            final var code = buildOptionalCode(payload);
            topicTemplate.convertAndSend(
                    new TopicMessageChannel(amazonSns, topicArn),
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
