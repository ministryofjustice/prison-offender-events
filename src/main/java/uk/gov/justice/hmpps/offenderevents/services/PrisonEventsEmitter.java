package uk.gov.justice.hmpps.offenderevents.services;

import com.amazonaws.services.sns.AmazonSNSAsync;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;
import uk.gov.justice.hmpps.sqs.HmppsQueueService;
import uk.gov.justice.hmpps.sqs.HmppsTopic;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PrisonEventsEmitter {

    private final AmazonSNSAsync prisonEventTopicSnsClient;
    private final String topicArn;
    private final ObjectMapper objectMapper;
    private final TelemetryClient telemetryClient;

    public PrisonEventsEmitter(final HmppsQueueService hmppsQueueService,
                               final ObjectMapper objectMapper,
                               final TelemetryClient telemetryClient) {
        HmppsTopic prisonEventTopic = hmppsQueueService.findByTopicId("prisoneventtopic");
        this.topicArn = prisonEventTopic.getArn();
        this.prisonEventTopicSnsClient = (AmazonSNSAsync) prisonEventTopic.getSnsClient();
        this.objectMapper = objectMapper;
        this.telemetryClient = telemetryClient;
    }

    public void sendEvent(final OffenderEvent payload) {
        try {
            prisonEventTopicSnsClient.publishAsync(new PublishRequest(topicArn, objectMapper.writeValueAsString(payload))
                .withMessageAttributes(metaData(payload)));
            telemetryClient.trackEvent(payload.getEventType(), asTelemetryMap(payload), null);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert payload {} to json", payload);
        }
    }

    private Map<String, MessageAttributeValue> metaData(final OffenderEvent payload) {
        final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("eventType", new MessageAttributeValue().withDataType("String").withStringValue(payload.getEventType()));
        messageAttributes.put("publishedAt", new MessageAttributeValue().withDataType("String").withStringValue(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
        Optional.ofNullable(buildOptionalCode(payload)).ifPresent(code -> messageAttributes.put("code", new MessageAttributeValue().withDataType("String").withStringValue(code)));
        return messageAttributes;
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
