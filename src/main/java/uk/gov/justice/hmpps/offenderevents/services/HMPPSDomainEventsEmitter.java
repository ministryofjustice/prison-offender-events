package uk.gov.justice.hmpps.offenderevents.services;

import com.amazonaws.services.sns.AmazonSNSAsync;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.aws.messaging.core.NotificationMessagingTemplate;
import org.springframework.cloud.aws.messaging.core.TopicMessageChannel;
import org.springframework.stereotype.Service;
import uk.gov.justice.hmpps.offenderevents.config.SqsConfigProperties;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.time.ZoneId.systemDefault;
import static uk.gov.justice.hmpps.offenderevents.config.SqsConfigPropertiesKt.hmppsEventTopic;

@Service
@Slf4j
public class HMPPSDomainEventsEmitter {
    private final NotificationMessagingTemplate topicTemplate;
    private final AmazonSNSAsync awsHMPPSEventsSnsClient;
    private final String topicArn;
    private final ObjectMapper objectMapper;
    private final ReceivePrisonerReasonCalculator receivePrisonerReasonCalculator;
    private final ReleasePrisonerReasonCalculator releasePrisonerReasonCalculator;
    private final TelemetryClient telemetryClient;


    HMPPSDomainEventsEmitter(@Qualifier("awsHMPPSEventsSnsClient") final AmazonSNSAsync awsHMPPSEventsSnsClient,
                             SqsConfigProperties sqsConfigProperties,
                             final ObjectMapper objectMapper,
                             final ReceivePrisonerReasonCalculator receivePrisonerReasonCalculator,
                             final ReleasePrisonerReasonCalculator releasePrisonerReasonCalculator,
                             final TelemetryClient telemetryClient) {
        this.topicTemplate = new NotificationMessagingTemplate(awsHMPPSEventsSnsClient);
        this.topicArn = hmppsEventTopic(sqsConfigProperties).getTopicArn();
        this.awsHMPPSEventsSnsClient = awsHMPPSEventsSnsClient;
        this.objectMapper = objectMapper;
        this.receivePrisonerReasonCalculator = receivePrisonerReasonCalculator;
        this.releasePrisonerReasonCalculator = releasePrisonerReasonCalculator;
        this.telemetryClient = telemetryClient;
    }

    public void convertAndSendWhenSignificant(OffenderEvent event) {
        final Optional<HMPPSDomainEvent> hmppsEvent = switch (event.getEventType()) {
            case "OFFENDER_MOVEMENT-RECEPTION" -> toPrisonerReceived(event);
            case "OFFENDER_MOVEMENT-DISCHARGE" -> toPrisonerReleased(event);
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
            .ifPresent(currentLocation -> elements.put("currentLocation", currentLocation.name()));

        Optional
            .ofNullable(hmppsDomainEvent.additionalInformation().currentPrisonStatus())
            .ifPresent(currentPrisonStatus -> elements.put("currentPrisonStatus", currentPrisonStatus.name()));

        return elements;
    }

    private Map<String, String> asTelemetryMap(OffenderEvent event, PrisonerMovementReason reason, String reasonDescription) {
        var elements = new HashMap<>(Map.of(
            "occurredAt",
            event.getEventDatetime().format(DateTimeFormatter.ISO_DATE_TIME),
            "nomsNumber",
            event.getOffenderIdDisplay(),
            "reason",
            reasonDescription,
            "prisonId",
            reason.prisonId()
        ));

        Optional
            .ofNullable(reason.details())
            .ifPresent(details -> elements.put("details", details));

        Optional
            .ofNullable(reason.currentLocation())
            .ifPresent(currentLocation -> elements.put("currentLocation", currentLocation.name()));

        Optional
            .ofNullable(reason.currentPrisonStatus())
            .ifPresent(currentPrisonStatus -> elements.put("currentPrisonStatus", currentPrisonStatus.name()));

        return elements;
    }

    private Optional<HMPPSDomainEvent> toPrisonerReceived(OffenderEvent event) {
        final var offenderNumber = event.getOffenderIdDisplay();
        final var receivedReason = receivePrisonerReasonCalculator.calculateMostLikelyReasonForPrisonerReceive(offenderNumber);

        if (!receivedReason.hasPrisonerActuallyBeenReceived()) {
            telemetryClient.trackEvent("prison-offender-events.prisoner.not-received", asTelemetryMap(event, receivedReason, receivedReason
                .reason()
                .name()), null);
            return Optional.empty();
        }
        return Optional.of(new HMPPSDomainEvent("prison-offender-events.prisoner.received",
            new AdditionalInformation(offenderNumber,
                receivedReason.reason().name(),
                receivedReason.source().name(),
                receivedReason.details(),
                receivedReason.currentLocation(),
                receivedReason.prisonId(),
                receivedReason.currentPrisonStatus()
            ),
            event.getEventDatetime(),
            "A prisoner has been received into prison"));
    }

    private Optional<HMPPSDomainEvent> toPrisonerReleased(OffenderEvent event) {
        final var offenderNumber = event.getOffenderIdDisplay();
        final var releaseReason = releasePrisonerReasonCalculator.calculateReasonForRelease(offenderNumber);

        if (!releaseReason.hasPrisonerActuallyBeenRelease()) {
            telemetryClient.trackEvent("prison-offender-events.prisoner.not-released", asTelemetryMap(event, releaseReason, releaseReason
                .reason()
                .name()), null);
            return Optional.empty();
        }

        return Optional.of(new HMPPSDomainEvent("prison-offender-events.prisoner.released",
            new AdditionalInformation(offenderNumber,
                releaseReason.reason().name(),
                null,
                releaseReason.details(),
                releaseReason.currentLocation(),
                releaseReason.prisonId(),
                releaseReason.currentPrisonStatus()
            ),
            event.getEventDatetime(),
            "A prisoner has been released from prison"));
    }

    public void sendEvent(final HMPPSDomainEvent payload) {
        try {
            topicTemplate.convertAndSend(
                new TopicMessageChannel(awsHMPPSEventsSnsClient, topicArn),
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
            occurredAt.atZone(ZoneId.of("Europe/London")).toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            OffsetDateTime
                .now()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
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
