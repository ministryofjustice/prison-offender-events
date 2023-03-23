package uk.gov.justice.hmpps.offenderevents.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.applicationinsights.TelemetryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import uk.gov.justice.hmpps.offenderevents.config.OffenderEventsProperties;
import uk.gov.justice.hmpps.offenderevents.model.HmppsDomainEvent;
import uk.gov.justice.hmpps.offenderevents.model.HmppsDomainEvent.PersonReference;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.ProbableCause;
import uk.gov.justice.hmpps.sqs.HmppsQueueService;
import uk.gov.justice.hmpps.sqs.HmppsTopic;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;

@Service
@Slf4j
public class HMPPSDomainEventsEmitter {
    private final SnsAsyncClient hmppsEventsTopicSnsClient;
    private final String topicArn;
    private final ObjectMapper objectMapper;
    private final ReceivePrisonerReasonCalculator receivePrisonerReasonCalculator;
    private final ReleasePrisonerReasonCalculator releasePrisonerReasonCalculator;
    private final MergeRecordDiscriminator mergeRecordDiscriminator;
    private final TelemetryClient telemetryClient;
    private final OffenderEventsProperties offenderEventsProperties;

    HMPPSDomainEventsEmitter(final HmppsQueueService hmppsQueueService,
                             final ObjectMapper objectMapper,
                             final ReceivePrisonerReasonCalculator receivePrisonerReasonCalculator,
                             final ReleasePrisonerReasonCalculator releasePrisonerReasonCalculator,
                             final MergeRecordDiscriminator mergeRecordDiscriminator,
                             final TelemetryClient telemetryClient,
                             final OffenderEventsProperties offenderEventsProperties) {
        HmppsTopic hmppsEventTopic = hmppsQueueService.findByTopicId("hmppseventtopic");
        this.topicArn = hmppsEventTopic.getArn();
        this.hmppsEventsTopicSnsClient = hmppsEventTopic.getSnsClient();
        this.objectMapper = objectMapper;
        this.receivePrisonerReasonCalculator = receivePrisonerReasonCalculator;
        this.releasePrisonerReasonCalculator = releasePrisonerReasonCalculator;
        this.mergeRecordDiscriminator = mergeRecordDiscriminator;
        this.telemetryClient = telemetryClient;
        this.offenderEventsProperties = offenderEventsProperties;
    }

    public void convertAndSendWhenSignificant(OffenderEvent event) {
        final List<HmppsDomainEvent> hmppsEvents =
            event.getCaseNoteId() != null ? toCaseNotePublished(event).stream().toList()
                : switch (event.getEventType()) {
                case "OFFENDER_MOVEMENT-RECEPTION" -> toPrisonerReceived(event).stream().toList();
                case "OFFENDER_MOVEMENT-DISCHARGE" -> toPrisonerReleased(event).stream().toList();
                case "BOOKING_NUMBER-CHANGED" -> toMergedOffenderNumbers(event);
                case "BED_ASSIGNMENT_HISTORY-INSERTED" -> toCellMove(event).stream().toList();
                default -> Collections.emptyList();
            };

        hmppsEvents.forEach(hmppsDomainEvent -> {
            sendEvent(hmppsDomainEvent);
            telemetryClient.trackEvent(hmppsDomainEvent.getEventType(), hmppsDomainEvent.asTelemetryMap(), null);
        });
    }

    private Optional<HmppsDomainEvent> toCellMove(final OffenderEvent event) {
        return Optional.of(HmppsDomainEvent.builder()
            .eventType("prison-offender-events.prisoner.cell.move")
            .description("A prisoner has been moved to a different cell")
            .occurredAt(toOccurredAt(event))
            .publishedAt(OffsetDateTime.now().toString())
            .personReference(new PersonReference(event.getOffenderIdDisplay()))
            .build()
            .withAdditionalInformation("nomsNumber", event.getOffenderIdDisplay())
            .withAdditionalInformation("livingUnitId", event.getLivingUnitId())
            .withAdditionalInformation("bedAssignmentSeq", event.getBedAssignmentSeq()));
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

    private Optional<HmppsDomainEvent> toPrisonerReceived(OffenderEvent event) {
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
        return Optional.of(HmppsDomainEvent.builder()
            .eventType("prison-offender-events.prisoner.received")
            .description("A prisoner has been received into prison")
            .occurredAt(toOccurredAt(event))
            .publishedAt(OffsetDateTime.now().toString())
            .personReference(new PersonReference(event.getOffenderIdDisplay()))
            .build()
            .withAdditionalInformation("nomsNumber", offenderNumber)
            .withAdditionalInformation("reason", receivedReason.reason().name())
            .withAdditionalInformation("probableCause", Optional.ofNullable(receivedReason.probableCause()).map(ProbableCause::name).orElse(null))
            .withAdditionalInformation("source", receivedReason.source().name())
            .withAdditionalInformation("details", receivedReason.details())
            .withAdditionalInformation("currentLocation", receivedReason.currentLocation().name())
            .withAdditionalInformation("prisonId", receivedReason.prisonId())
            .withAdditionalInformation("nomisMovementReasonCode", receivedReason.nomisMovementReason().code())
            .withAdditionalInformation("currentPrisonStatus", receivedReason.currentPrisonStatus().name()));
    }

    private List<HmppsDomainEvent> toMergedOffenderNumbers(OffenderEvent event) {
        final var mergeResults = mergeRecordDiscriminator.identifyMergedPrisoner(event.getBookingId());

        return mergeResults.stream()
            .map(mergeResult -> HmppsDomainEvent.builder()
                .eventType("prison-offender-events.prisoner.merged")
                .description(format("A prisoner has been merged from %s to %s", mergeResult.mergedNumber(), mergeResult.remainingNumber()))
                .occurredAt(toOccurredAt(event))
                .publishedAt(OffsetDateTime.now().toString())
                .personReference(new PersonReference(mergeResult.remainingNumber()))
                .build()
                .withAdditionalInformation("nomsNumber", mergeResult.remainingNumber())
                .withAdditionalInformation("removedNomsNumber", mergeResult.mergedNumber())
                .withAdditionalInformation("reason", "MERGE"))
            .toList();
    }

    private Optional<HmppsDomainEvent> toPrisonerReleased(OffenderEvent event) {
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

        return Optional.of(HmppsDomainEvent.builder()
            .eventType("prison-offender-events.prisoner.released")
            .description("A prisoner has been released from prison")
            .occurredAt(toOccurredAt(event))
            .publishedAt(OffsetDateTime.now().toString())
            .personReference(new PersonReference(offenderNumber))
            .build()
            .withAdditionalInformation("nomsNumber", offenderNumber)
            .withAdditionalInformation("reason", releaseReason.reason().name())
            .withAdditionalInformation("details", releaseReason.details())
            .withAdditionalInformation("currentLocation", releaseReason.currentLocation().name())
            .withAdditionalInformation("prisonId", releaseReason.prisonId())
            .withAdditionalInformation("nomisMovementReasonCode", releaseReason.nomisMovementReason().code())
            .withAdditionalInformation("currentPrisonStatus", releaseReason.currentPrisonStatus().name()));
    }

    private Optional<HmppsDomainEvent> toCaseNotePublished(OffenderEvent event) {
        // SDI-594: If there is no offender id then this means that the case note has actually been removed instead
        // This means that we can ignore this event - will be handled by the offender deletion event instead.
        if (event.getOffenderIdDisplay() == null) {
            log.warn("Ignoring case note published event for case note {} as offender id display is null", event.getCaseNoteId());
            return Optional.empty();
        }

        return Optional.of(HmppsDomainEvent.builder()
            .eventType("prison.case-note.published")
            .description("A prison case note has been created or amended")
            .detailUrl(String.format("%s/case-notes/%s/%d", offenderEventsProperties.getCasenotesApiBaseUrl(), event.getOffenderIdDisplay(), event.getCaseNoteId()))
            .occurredAt(toOccurredAt(event))
            .publishedAt(OffsetDateTime.now().toString())
            .personReference(new PersonReference(event.getOffenderIdDisplay()))
            .build()
            .withAdditionalInformation("caseNoteId", event.getCaseNoteId().toString())
            .withAdditionalInformation("caseNoteType", String.format("%s-%s", event.getCaseNoteType(), event.getCaseNoteSubType().split("\\W")[0]))
            .withAdditionalInformation("type", event.getCaseNoteType())
            .withAdditionalInformation("subType", event.getCaseNoteSubType()));

    }

    private String toOccurredAt(OffenderEvent event) {
        return event.getEventDatetime().atZone(ZoneId.of("Europe/London")).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public void sendEvent(final HmppsDomainEvent payload) {
        try {
            hmppsEventsTopicSnsClient.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(objectMapper.writeValueAsString(payload))
                .messageAttributes(payload.asMetadataMap()).build());
        } catch (JsonProcessingException e) {
            log.error("Failed to convert payload {} to json", payload);
        }
    }
}
