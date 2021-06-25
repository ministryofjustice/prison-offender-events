package uk.gov.justice.hmpps.offenderevents.services;

import com.amazonaws.services.sns.AmazonSNSAsync;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
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
import java.util.HashMap;
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
    private final ReleasePrisonerReasonCalculator releasePrisonerReasonCalculator;
    private final TelemetryClient telemetryClient;


    HMPPSDomainEventsEmitter(@Qualifier("awsHMPPSEventsSnsClient") final AmazonSNSAsync amazonSns,
                             @Value("${hmpps.sns.topic.arn}") final String topicArn,
                             final ObjectMapper objectMapper,
                             final ReceivePrisonerReasonCalculator receivePrisonerReasonCalculator,
                             final ReleasePrisonerReasonCalculator releasePrisonerReasonCalculator,
                             final TelemetryClient telemetryClient) {
        this.topicTemplate = new NotificationMessagingTemplate(amazonSns);
        this.topicArn = topicArn;
        this.amazonSns = amazonSns;
        this.objectMapper = objectMapper;
        this.receivePrisonerReasonCalculator = receivePrisonerReasonCalculator;
        this.releasePrisonerReasonCalculator = releasePrisonerReasonCalculator;
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
        var elements = new HashMap<>(Map.of("occurredAt",
            hmppsDomainEvent.occurredAt(),
            "nomsNumber",
            hmppsDomainEvent.additionalInformation().nomsNumber(),
            "reason",
            hmppsDomainEvent.additionalInformation().reason(),
            "prisonId",
            hmppsDomainEvent.additionalInformation().prisonId()
        ));

        Optional
            .ofNullable(hmppsDomainEvent.additionalInformation().source())
            .ifPresent(source -> elements.put("source", source));
        Optional
            .ofNullable(hmppsDomainEvent.additionalInformation().details())
            .ifPresent(details -> elements.put("details", details));

        Optional
            .ofNullable(hmppsDomainEvent.additionalInformation().currentLocation())
            .ifPresent(source -> elements.put("currentLocation", hmppsDomainEvent
                .additionalInformation()
                .currentLocation()
                .name()));

        Optional
            .ofNullable(hmppsDomainEvent.additionalInformation().currentPrisonStatus())
            .ifPresent(source -> elements.put("currentPrisonStatus", hmppsDomainEvent
                .additionalInformation()
                .currentPrisonStatus()
                .name()));

        return elements;
    }

    private HMPPSDomainEvent toPrisonerReceived(OffenderEvent event) {
        final var offenderNumber = event.getOffenderIdDisplay();
        final var receivedReason = receivePrisonerReasonCalculator.calculateMostLikelyReasonForPrisonerReceive(offenderNumber);
        return new HMPPSDomainEvent("prison-offender-events.prisoner.received",
            new AdditionalInformation(offenderNumber,
                receivedReason.reason().name(),
                receivedReason.source().name(),
                receivedReason.details(),
                receivedReason.currentLocation(),
                receivedReason.prisonId(),
                receivedReason.currentPrisonStatus()
            ),
            event.getEventDatetime(),
            "A prisoner has been received into prison");
    }

    private HMPPSDomainEvent toPrisonerReleased(OffenderEvent event) {
        final var offenderNumber = event.getOffenderIdDisplay();
        final var releaseReason = releasePrisonerReasonCalculator.calculateReasonForRelease(offenderNumber);
        return new HMPPSDomainEvent("prison-offender-events.prisoner.released",
            new AdditionalInformation(offenderNumber,
                releaseReason.reason().name(),
                null,
                releaseReason.details(),
                releaseReason.currentLocation(),
                releaseReason.lastLocationId(),
                releaseReason.currentPrisonStatus()
            ),
            event.getEventDatetime(),
            "A prisoner has been released from prison");
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

@JsonInclude(Include.NON_NULL)
record AdditionalInformation(String nomsNumber,
                             String reason,
                             String source,
                             String details,
                             CurrentLocation currentLocation,
                             String prisonId,
                             CurrentPrisonStatus currentPrisonStatus) {
}
