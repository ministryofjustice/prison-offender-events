package uk.gov.justice.hmpps.offenderevents.services

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.hmpps.offenderevents.config.OffenderEventsProperties
import uk.gov.justice.hmpps.offenderevents.config.trackEvent
import uk.gov.justice.hmpps.offenderevents.model.HmppsDomainEvent
import uk.gov.justice.hmpps.offenderevents.model.HmppsDomainEvent.PersonReference
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent
import uk.gov.justice.hmpps.offenderevents.services.MergeRecordDiscriminator.MergeOutcome
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.ReceiveReason
import uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.ReleaseReason
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Optional
import java.util.function.Consumer

@Service
class HMPPSDomainEventsEmitter(
  hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
  private val receivePrisonerReasonCalculator: ReceivePrisonerReasonCalculator,
  private val releasePrisonerReasonCalculator: ReleasePrisonerReasonCalculator,
  private val mergeRecordDiscriminator: MergeRecordDiscriminator,
  private val telemetryClient: TelemetryClient,
  private val offenderEventsProperties: OffenderEventsProperties,
) {
  private val hmppsEventsTopicSnsClient: SnsAsyncClient
  private val topicArn: String

  init {
    val hmppsEventTopic = hmppsQueueService.findByTopicId("hmppseventtopic")
    topicArn = hmppsEventTopic!!.arn
    hmppsEventsTopicSnsClient = hmppsEventTopic.snsClient
  }

  fun convertAndSendWhenSignificant(event: OffenderEvent) {
    val hmppsEvents =
      if (event.caseNoteId != null) {
        toCaseNotePublished(event).stream().toList()
      } else {
        when (event.eventType) {
          "OFFENDER_MOVEMENT-RECEPTION" -> toPrisonerReceived(event).stream().toList()
          "OFFENDER_MOVEMENT-DISCHARGE" -> toPrisonerReleased(event).stream().toList()
          "BOOKING_NUMBER-CHANGED" -> toMergedOffenderNumbers(event)
          "BED_ASSIGNMENT_HISTORY-INSERTED" -> toCellMove(event).stream().toList()
          else -> emptyList()
        }
      }
    hmppsEvents.forEach(
      Consumer { hmppsDomainEvent: HmppsDomainEvent ->
        sendEvent(hmppsDomainEvent)
        telemetryClient.trackEvent(hmppsDomainEvent.eventType, hmppsDomainEvent.asTelemetryMap(), null)
      },
    )
  }

  private fun toCellMove(event: OffenderEvent): Optional<HmppsDomainEvent> {
    return Optional.of(
      HmppsDomainEvent.builder()
        .eventType("prison-offender-events.prisoner.cell.move")
        .description("A prisoner has been moved to a different cell")
        .occurredAt(toOccurredAt(event))
        .publishedAt(OffsetDateTime.now().toString())
        .personReference(PersonReference(event.offenderIdDisplay))
        .build()
        .withAdditionalInformation("nomsNumber", event.offenderIdDisplay)
        .withAdditionalInformation("livingUnitId", event.livingUnitId)
        .withAdditionalInformation("bedAssignmentSeq", event.bedAssignmentSeq)
        .withAdditionalInformation("bookingId", event.bookingId),
    )
  }

  private fun asTelemetryMap(
    event: OffenderEvent,
    reason: PrisonerMovementReason,
    reasonDescription: String,
  ): Map<String, String> {
    val elements = mutableMapOf(
      "occurredAt" to event.eventDatetime.format(DateTimeFormatter.ISO_DATE_TIME),
      "nomsNumber" to event.offenderIdDisplay,
      "reason" to reasonDescription,
      "prisonId" to reason.prisonId,
      "currentLocation" to reason.currentLocation.name,
      "currentPrisonStatus" to reason.currentPrisonStatus.name,
    )
    reason.details?.let { elements["details"] = it }
    return elements
  }

  private fun toPrisonerReceived(event: OffenderEvent): Optional<HmppsDomainEvent> {
    val offenderNumber = event.offenderIdDisplay
    val receivedReason: ReceiveReason = try {
      receivePrisonerReasonCalculator.calculateMostLikelyReasonForPrisonerReceive(offenderNumber)
    } catch (e: WebClientResponseException) {
      if (e.statusCode == HttpStatus.NOT_FOUND) {
        // Possibly the offender has been merged since the receive event
        log.warn("Ignoring receive event for {} who no longer exists", offenderNumber)
        return Optional.empty()
      }
      throw e
    }
    if (!receivedReason.hasPrisonerActuallyBeenReceived()) {
      telemetryClient.trackEvent(
        "prison-offender-events.prisoner.not-received",
        asTelemetryMap(
          event,
          receivedReason,
          receivedReason.reason.name,
        ),
      )
      return Optional.empty()
    }
    return Optional.of(
      HmppsDomainEvent.builder()
        .eventType("prison-offender-events.prisoner.received")
        .description("A prisoner has been received into prison")
        .occurredAt(toOccurredAt(event))
        .publishedAt(OffsetDateTime.now().toString())
        .personReference(PersonReference(event.offenderIdDisplay))
        .build()
        .withAdditionalInformation("nomsNumber", offenderNumber)
        .withAdditionalInformation("reason", receivedReason.reason.name)
        .withAdditionalInformation("probableCause", receivedReason.probableCause?.name)
        .withAdditionalInformation("source", receivedReason.source.name)
        .withAdditionalInformation("details", receivedReason.details)
        .withAdditionalInformation("currentLocation", receivedReason.currentLocation.name)
        .withAdditionalInformation("prisonId", receivedReason.prisonId)
        .withAdditionalInformation("nomisMovementReasonCode", receivedReason.nomisMovementReason.code)
        .withAdditionalInformation("currentPrisonStatus", receivedReason.currentPrisonStatus.name),
    )
  }

  private fun toMergedOffenderNumbers(event: OffenderEvent): List<HmppsDomainEvent> {
    val mergeResults = mergeRecordDiscriminator.identifyMergedPrisoner(event.bookingId)
    return mergeResults.stream()
      .map { mergeResult: MergeOutcome ->
        HmppsDomainEvent.builder()
          .eventType("prison-offender-events.prisoner.merged")
          .description(
            "A prisoner has been merged from ${mergeResult.mergedNumber} to ${mergeResult.remainingNumber}",
          )
          .occurredAt(toOccurredAt(event))
          .publishedAt(OffsetDateTime.now().toString())
          .personReference(PersonReference(mergeResult.remainingNumber))
          .build()
          .withAdditionalInformation("nomsNumber", mergeResult.remainingNumber)
          .withAdditionalInformation("removedNomsNumber", mergeResult.mergedNumber)
          .withAdditionalInformation("reason", "MERGE")
      }
      .toList()
  }

  private fun toPrisonerReleased(event: OffenderEvent): Optional<HmppsDomainEvent> {
    val offenderNumber = event.offenderIdDisplay
    val releaseReason: ReleaseReason = try {
      releasePrisonerReasonCalculator.calculateReasonForRelease(offenderNumber)
    } catch (e: WebClientResponseException) {
      if (e.statusCode == HttpStatus.NOT_FOUND) {
        // Possibly the offender has been merged since the discharge event
        log.warn("Ignoring release event for {} who no longer exists", offenderNumber)
        return Optional.empty()
      }
      throw e
    }
    if (!releaseReason.hasPrisonerActuallyBeenRelease()) {
      telemetryClient.trackEvent(
        "prison-offender-events.prisoner.not-released",
        asTelemetryMap(event, releaseReason, releaseReason.reason.name),
      )
      return Optional.empty()
    }
    return Optional.of(
      HmppsDomainEvent.builder()
        .eventType("prison-offender-events.prisoner.released")
        .description("A prisoner has been released from prison")
        .occurredAt(toOccurredAt(event))
        .publishedAt(OffsetDateTime.now().toString())
        .personReference(PersonReference(offenderNumber))
        .build()
        .withAdditionalInformation("nomsNumber", offenderNumber)
        .withAdditionalInformation("reason", releaseReason.reason.name)
        .withAdditionalInformation("details", releaseReason.details)
        .withAdditionalInformation("currentLocation", releaseReason.currentLocation.name)
        .withAdditionalInformation("prisonId", releaseReason.prisonId)
        .withAdditionalInformation("nomisMovementReasonCode", releaseReason.nomisMovementReason.code)
        .withAdditionalInformation("currentPrisonStatus", releaseReason.currentPrisonStatus.name),
    )
  }

  private fun toCaseNotePublished(event: OffenderEvent): Optional<HmppsDomainEvent> {
    // SDI-594: If there is no offender id then this means that the case note has actually been removed instead
    // This means that we can ignore this event - will be handled by the offender deletion event instead.
    if (event.offenderIdDisplay == null) {
      log.warn(
        "Ignoring case note published event for case note {} as offender id display is null",
        event.caseNoteId,
      )
      return Optional.empty()
    }
    return Optional.of(
      HmppsDomainEvent.builder()
        .eventType("prison.case-note.published")
        .description("A prison case note has been created or amended")
        .detailUrl(
          "${offenderEventsProperties.casenotesApiBaseUrl}/case-notes/${event.offenderIdDisplay}/${event.caseNoteId}",
        )
        .occurredAt(toOccurredAt(event))
        .publishedAt(OffsetDateTime.now().toString())
        .personReference(PersonReference(event.offenderIdDisplay))
        .build()
        .withAdditionalInformation("caseNoteId", event.caseNoteId.toString())
        .withAdditionalInformation(
          "caseNoteType",
          "${event.caseNoteType}-${
          event.caseNoteSubType.split("\\W".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()[0]
          }",
        )
        .withAdditionalInformation("type", event.caseNoteType)
        .withAdditionalInformation("subType", event.caseNoteSubType),
    )
  }

  private fun toOccurredAt(event: OffenderEvent): String =
    event.eventDatetime.atZone(ZoneId.of("Europe/London")).toOffsetDateTime()
      .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

  fun sendEvent(payload: HmppsDomainEvent) {
    try {
      hmppsEventsTopicSnsClient.publish(
        PublishRequest.builder()
          .topicArn(topicArn)
          .message(objectMapper.writeValueAsString(payload))
          .messageAttributes(payload.asMetadataMap()).build(),
      )
    } catch (e: JsonProcessingException) {
      log.error("Failed to convert payload {} to json", payload)
    }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
