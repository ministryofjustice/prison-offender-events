package uk.gov.justice.hmpps.offenderevents.services;

import com.amazonaws.services.sns.AmazonSNSAsync;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.aws.messaging.core.NotificationMessagingTemplate;
import org.springframework.cloud.aws.messaging.core.TopicMessageChannel;
import org.springframework.stereotype.Service;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class HMPPSDomainEventsEmitter {
    private final NotificationMessagingTemplate topicTemplate;
    private final AmazonSNSAsync amazonSns;
    private final String topicArn;
    private final ObjectMapper objectMapper;
    private final ReceivePrisonerReasonCalculator receivePrisonerReasonCalculator;
    private final TelemetryClient telemetryClient;


    HMPPSDomainEventsEmitter(@Qualifier("awsHMPPSEventsSnsClient") final AmazonSNSAsync amazonSns,
                             @Value("${hmpps.sns.topic.arn}") final String topicArn,
                             final ObjectMapper objectMapper,
                             final ReceivePrisonerReasonCalculator receivePrisonerReasonCalculator,
                             final TelemetryClient telemetryClient) {
        this.topicTemplate = new NotificationMessagingTemplate(amazonSns);
        this.topicArn = topicArn;
        this.amazonSns = amazonSns;
        this.objectMapper = objectMapper;
        this.receivePrisonerReasonCalculator = receivePrisonerReasonCalculator;
        this.telemetryClient = telemetryClient;
    }

    public void convertAndSendWhenSignificant(OffenderEvent event) {
        final Optional<HMPPSDomainEvent> hmppsEvent = switch (event.getEventType()) {
            case "OFFENDER_MOVEMENT-RECEPTION" -> Optional.of(toPrisonerReceived(event));
            case "OFFENDER_MOVEMENT-DISCHARGE" -> Optional.of(toPrisonerReleased(event));
            default -> Optional.empty();
        };

        hmppsEvent.ifPresent(hmppsDomainEvent -> {
            sendEvent(hmppsDomainEvent);
            telemetryClient.trackEvent(hmppsDomainEvent.eventType(), asTelemetryMap(hmppsDomainEvent), null);
        });
    }

    private Map<String, String> asTelemetryMap(HMPPSDomainEvent hmppsDomainEvent) {
        return Map.of("occurredAt",
            hmppsDomainEvent.occurredAt(),
            "nomsNumber",
            hmppsDomainEvent.additionalInformation().nomsNumber(),
            "reason",
            hmppsDomainEvent.additionalInformation().reason(),
            "source",
            Optional.ofNullable(hmppsDomainEvent.additionalInformation().source()).orElse("unknown"),
            "details",
            Optional.ofNullable(hmppsDomainEvent.additionalInformation().details()).orElse("")
            );
    }

    private HMPPSDomainEvent toPrisonerReceived(OffenderEvent event) {
        final var offenderNumber = event.getOffenderIdDisplay();
        final var receivedReason  =  receivePrisonerReasonCalculator.calculateMostLikelyReasonForPrisoner(offenderNumber);
        return new HMPPSDomainEvent("prison-offender-events.prisoner.received",
            new AdditionalInformation(offenderNumber,
                receivedReason.reason().name(),
                receivedReason.source().name(),
                receivedReason.details()
            ),
            event.getEventDatetime(),
            "A prisoner has been received into prison");
    }

    private HMPPSDomainEvent toPrisonerReleased(OffenderEvent event) {
        return new HMPPSDomainEvent("prison-offender-events.prisoner.released", new AdditionalInformation(event.getOffenderIdDisplay(), "UNKNOWN"), event
            .getEventDatetime(), "A prisoner has been released from prison");
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

record HMPPSDomainEvent(String eventType, AdditionalInformation additionalInformation, int version,
                        String occurredAt, String publishedAt, String description) {
    public HMPPSDomainEvent(String eventType,
                            AdditionalInformation additionalInformation,
                            LocalDateTime occurredAt,
                            String description) {
        this(eventType,
            additionalInformation,
            1,
            occurredAt.format(DateTimeFormatter.ISO_DATE_TIME),
            LocalDateTime
                .now()
                .format(DateTimeFormatter.ISO_DATE_TIME),
            description);
    }
}

record AdditionalInformation(String nomsNumber, String reason, String source, String details) {
    AdditionalInformation(String nomsNumber, String reason) {
        this(nomsNumber, reason, null, null);
    }
}
