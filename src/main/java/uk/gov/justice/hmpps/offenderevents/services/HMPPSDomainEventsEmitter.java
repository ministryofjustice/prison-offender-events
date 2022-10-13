package uk.gov.justice.hmpps.offenderevents.services;

import com.amazonaws.services.sns.AmazonSNSAsync;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.ProbableCause;
import uk.gov.justice.hmpps.sqs.HmppsQueueService;
import uk.gov.justice.hmpps.sqs.HmppsTopic;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Service
@Slf4j
public class HMPPSDomainEventsEmitter {
    private final AmazonSNSAsync hmppsEventsTopicSnsClient;
    private final String topicArn;
    private final ObjectMapper objectMapper;
    private final ReceivePrisonerReasonCalculator receivePrisonerReasonCalculator;
    private final ReleasePrisonerReasonCalculator releasePrisonerReasonCalculator;
    private final MergeRecordDiscriminator mergeRecordDiscriminator;
    private final TelemetryClient telemetryClient;

    HMPPSDomainEventsEmitter(final HmppsQueueService hmppsQueueService,
                             final ObjectMapper objectMapper,
                             final ReceivePrisonerReasonCalculator receivePrisonerReasonCalculator,
                             final ReleasePrisonerReasonCalculator releasePrisonerReasonCalculator,
                             final MergeRecordDiscriminator mergeRecordDiscriminator,
                             final TelemetryClient telemetryClient) {
        HmppsTopic hmppsEventTopic = hmppsQueueService.findByTopicId("hmppseventtopic");
        this.topicArn = hmppsEventTopic.getArn();
        this.hmppsEventsTopicSnsClient = (AmazonSNSAsync) hmppsEventTopic.getSnsClient();
        this.objectMapper = objectMapper;
        this.receivePrisonerReasonCalculator = receivePrisonerReasonCalculator;
        this.releasePrisonerReasonCalculator = releasePrisonerReasonCalculator;
        this.mergeRecordDiscriminator = mergeRecordDiscriminator;
        this.telemetryClient = telemetryClient;
    }

    public void convertAndSendWhenSignificant(OffenderEvent event) {
        final List<HMPPSDomainEvent> hmppsEvents = switch (event.getEventType()) {
            case "OFFENDER_MOVEMENT-RECEPTION" -> toPrisonerReceived(event).stream().collect(Collectors.toList());
            case "OFFENDER_MOVEMENT-DISCHARGE" -> toPrisonerReleased(event).stream().collect(Collectors.toList());
            case "BOOKING_NUMBER-CHANGED" -> toMergedOffenderNumbers(event);
            default -> Collections.emptyList();
        };

        hmppsEvents.forEach(hmppsDomainEvent -> {
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
            hmppsDomainEvent.additionalInformation().reason()
        ));

        Optional
            .ofNullable(hmppsDomainEvent.additionalInformation().prisonId())
            .ifPresent(prisonId -> elements.put("prisonId", prisonId));

        Optional
            .ofNullable(hmppsDomainEvent.additionalInformation().probableCause())
            .ifPresent(probableCause -> elements.put("probableCause", probableCause));

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

        Optional
            .ofNullable(hmppsDomainEvent.additionalInformation().removedNomsNumber())
            .ifPresent(removedNomsNumber -> elements.put("removedNomsNumber", removedNomsNumber));

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
        final ReceivePrisonerReasonCalculator.ReceiveReason receivedReason;
        try {
            receivedReason = receivePrisonerReasonCalculator.calculateMostLikelyReasonForPrisonerReceive(offenderNumber);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                // Possibly the offender has been merged since the receive event
                log.warn("Ignoring receive event for {} who no longer exists", offenderNumber);
                return Optional.empty();
            }
            throw e;
        }

        if (!receivedReason.hasPrisonerActuallyBeenReceived()) {
            telemetryClient.trackEvent("prison-offender-events.prisoner.not-received", asTelemetryMap(event, receivedReason, receivedReason
                .reason()
                .name()), null);
            return Optional.empty();
        }
        return Optional.of(new HMPPSDomainEvent("prison-offender-events.prisoner.received",
            new AdditionalInformation(offenderNumber,
                receivedReason.reason().name(),
                Optional.ofNullable(receivedReason.probableCause()).map(ProbableCause::name).orElse(null),
                receivedReason.source().name(),
                receivedReason.details(),
                receivedReason.currentLocation(),
                receivedReason.prisonId(),
                receivedReason.currentPrisonStatus(),
                null
            ),
            event.getEventDatetime(),
            "A prisoner has been received into prison"));
    }

    private List<HMPPSDomainEvent> toMergedOffenderNumbers(OffenderEvent event) {
        final var mergeResults = mergeRecordDiscriminator.identifyMergedPrisoner(event.getBookingId());

        return mergeResults.stream()
            .map(mergeResult ->
            new HMPPSDomainEvent("prison-offender-events.prisoner.merged",
                new AdditionalInformation(mergeResult.remainingNumber(), mergeResult.mergedNumber()),
                event.getEventDatetime(),
                format("A prisoner has been merged from %s to %s", mergeResult.mergedNumber(), mergeResult.remainingNumber()))
            )
       .collect(Collectors.toList());
    }

    private Optional<HMPPSDomainEvent> toPrisonerReleased(OffenderEvent event) {
        final var offenderNumber = event.getOffenderIdDisplay();
        final ReleasePrisonerReasonCalculator.ReleaseReason releaseReason;
        try {
            releaseReason = releasePrisonerReasonCalculator.calculateReasonForRelease(offenderNumber);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
                // Possibly the offender has been merged since the discharge event
                log.warn("Ignoring release event for {} who no longer exists", offenderNumber);
                return Optional.empty();
            }
            throw e;
        }
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
                null,
                releaseReason.details(),
                releaseReason.currentLocation(),
                releaseReason.prisonId(),
                releaseReason.currentPrisonStatus(),
             null
            ),
            event.getEventDatetime(),
            "A prisoner has been released from prison"));
    }

    public void sendEvent(final HMPPSDomainEvent payload) {
        try {
            hmppsEventsTopicSnsClient.publishAsync(new PublishRequest(topicArn, objectMapper.writeValueAsString(payload))
                .withMessageAttributes(metaData(payload)));
        } catch (JsonProcessingException e) {
            log.error("Failed to convert payload {} to json", payload);
        }
    }

    private Map<String, MessageAttributeValue> metaData(final HMPPSDomainEvent payload) {
        final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("eventType", new MessageAttributeValue().withDataType("String").withStringValue(payload.eventType()));
        return messageAttributes;
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
                             String probableCause,
                             String source,
                             String details,
                             CurrentLocation currentLocation,
                             String prisonId,
                             CurrentPrisonStatus currentPrisonStatus,
                             String removedNomsNumber) {

    public AdditionalInformation(String nomsNumber, String removedNomsNumber) {
        this(nomsNumber, "MERGE", null, null, null, null, null, null, removedNomsNumber);
    }
}
