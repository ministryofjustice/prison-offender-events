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
import uk.gov.justice.hmpps.offenderevents.model.CaseNoteOffenderEvent
import uk.gov.justice.hmpps.offenderevents.model.CellMoveOffenderEvent
import uk.gov.justice.hmpps.offenderevents.model.HmppsDomainEvent
import uk.gov.justice.hmpps.offenderevents.model.HmppsDomainEvent.PersonReference
import uk.gov.justice.hmpps.offenderevents.model.ImprisonmentStatusChangedEvent
import uk.gov.justice.hmpps.offenderevents.model.NonAssociationDetailsOffenderEvent
import uk.gov.justice.hmpps.offenderevents.model.OffenderContactEvent
import uk.gov.justice.hmpps.offenderevents.model.OffenderContactEventDeleted
import uk.gov.justice.hmpps.offenderevents.model.OffenderContactEventInserted
import uk.gov.justice.hmpps.offenderevents.model.OffenderContactEventUpdated
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent
import uk.gov.justice.hmpps.offenderevents.model.PersonRestrictionOffenderEvent
import uk.gov.justice.hmpps.offenderevents.model.PersonRestrictionOffenderEventDeleted
import uk.gov.justice.hmpps.offenderevents.model.PersonRestrictionOffenderEventUpserted
import uk.gov.justice.hmpps.offenderevents.model.PrisonerActivityUpdateEvent
import uk.gov.justice.hmpps.offenderevents.model.PrisonerAppointmentUpdateEvent
import uk.gov.justice.hmpps.offenderevents.model.PrisonerBookingMovedOffenderEvent
import uk.gov.justice.hmpps.offenderevents.model.PrisonerDischargedOffenderEvent
import uk.gov.justice.hmpps.offenderevents.model.PrisonerMergedOffenderEvent
import uk.gov.justice.hmpps.offenderevents.model.PrisonerReceivedOffenderEvent
import uk.gov.justice.hmpps.offenderevents.model.RestrictionOffenderEvent
import uk.gov.justice.hmpps.offenderevents.model.SentenceDatesChangedEvent
import uk.gov.justice.hmpps.offenderevents.model.VisitorRestrictionOffenderEvent
import uk.gov.justice.hmpps.offenderevents.model.VisitorRestrictionOffenderEventDeleted
import uk.gov.justice.hmpps.offenderevents.model.VisitorRestrictionOffenderEventUpserted
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.ReceiveReason
import uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.ReleaseReason
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.jvm.optionals.getOrNull

@Service
class HMPPSDomainEventsEmitter(
  hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
  private val receivePrisonerReasonCalculator: ReceivePrisonerReasonCalculator,
  private val releasePrisonerReasonCalculator: ReleasePrisonerReasonCalculator,
  private val telemetryClient: TelemetryClient,
  private val offenderEventsProperties: OffenderEventsProperties,
  private val prisonApiService: PrisonApiService,
) {
  private final val hmppsEventsTopicSnsClient: SnsAsyncClient
  private final val topicArn: String

  init {
    val hmppsEventTopic = hmppsQueueService.findByTopicId("hmppseventtopic")
    topicArn = hmppsEventTopic!!.arn
    hmppsEventsTopicSnsClient = hmppsEventTopic.snsClient
  }

  fun convertAndSendWhenSignificant(event: String, message: String) {
    val mapping = OffenderEvent.eventMappings[event] ?: return
    when (val offenderEvent = objectMapper.readValue(message, mapping)) {
      is CaseNoteOffenderEvent -> toCaseNotePublished(offenderEvent).toListOrEmptyWhenNull()
      is PrisonerReceivedOffenderEvent -> toPrisonerReceived(offenderEvent).toListOrEmptyWhenNull()
      is PrisonerDischargedOffenderEvent -> toPrisonerReleased(offenderEvent).toListOrEmptyWhenNull()
      is PrisonerMergedOffenderEvent -> toMergedOffenderNumbers(offenderEvent)
      is CellMoveOffenderEvent -> toCellMove(offenderEvent).toList()
      is NonAssociationDetailsOffenderEvent -> toNonAssociationDetails(offenderEvent).toList()
      is PersonRestrictionOffenderEventUpserted -> toPersonRestrictionUpserted(offenderEvent)
      is PersonRestrictionOffenderEventDeleted -> toPersonRestrictionDeleted(offenderEvent)
      is VisitorRestrictionOffenderEventUpserted -> offenderEvent.toDomainEvents()
      is VisitorRestrictionOffenderEventDeleted -> offenderEvent.toDomainEvents()
      is RestrictionOffenderEvent -> toRestriction(offenderEvent).toList()
      is PrisonerActivityUpdateEvent -> toActivityChanged(offenderEvent).toList()
      is PrisonerAppointmentUpdateEvent -> toAppointmentChanged(offenderEvent).toList()
      is ImprisonmentStatusChangedEvent -> toImprisonmentStatusChanged(offenderEvent).toListOrEmptyWhenNull()
      is SentenceDatesChangedEvent -> toSentenceDatesChanged(offenderEvent).toList()
      is OffenderContactEventInserted -> offenderEvent.toDomainEvent().toListOrEmptyWhenNull()
      is OffenderContactEventUpdated -> offenderEvent.toDomainEvent().toListOrEmptyWhenNull()
      is OffenderContactEventDeleted -> offenderEvent.toDomainEvent().toListOrEmptyWhenNull()
      is PrisonerBookingMovedOffenderEvent -> offenderEvent.toDomainEvent().toListOrEmptyWhenNull()

      else -> emptyList()
    }.also {
      sendEvents(it)
    }
  }

  private fun toCellMove(event: CellMoveOffenderEvent): HmppsDomainEvent = HmppsDomainEvent(
    eventType = "prison-offender-events.prisoner.cell.move",
    description = "A prisoner has been moved to a different cell",
    occurredAt = event.toOccurredAt(),
    publishedAt = OffsetDateTime.now().toString(),
    personReference = PersonReference(event.offenderIdDisplay),
  )
    .withAdditionalInformation("nomsNumber", event.offenderIdDisplay)
    .withAdditionalInformation("livingUnitId", event.livingUnitId)
    .withAdditionalInformation("bedAssignmentSeq", event.bedAssignmentSeq)
    .withAdditionalInformation("bookingId", event.bookingId)

  private fun asTelemetryMap(
    event: OffenderEvent,
    reason: PrisonerMovementReason,
    reasonDescription: String,
  ): Map<String, String?> {
    val elements = mutableMapOf(
      "occurredAt" to event.eventDatetime.format(DateTimeFormatter.ISO_DATE_TIME),
      "nomsNumber" to event.offenderIdDisplay,
      "reason" to reasonDescription,
      "prisonId" to reason.prisonId,
      "currentLocation" to reason.currentLocation?.name,
      "currentPrisonStatus" to reason.currentPrisonStatus?.name,
    )
    reason.details?.let { elements["details"] = it }
    return elements
  }

  private fun toPrisonerReceived(event: PrisonerReceivedOffenderEvent): HmppsDomainEvent? {
    val offenderNumber = event.offenderIdDisplay
    val receivedReason: ReceiveReason = try {
      receivePrisonerReasonCalculator.calculateMostLikelyReasonForPrisonerReceive(offenderNumber)
    } catch (e: WebClientResponseException) {
      if (e.statusCode == HttpStatus.NOT_FOUND) {
        // Possibly the offender has been merged since the receive event
        log.warn("Ignoring receive event for {} who no longer exists", offenderNumber)
        return null
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
        null,
      )
      return null
    }
    return HmppsDomainEvent(
      eventType = "prison-offender-events.prisoner.received",
      description = "A prisoner has been received into prison",
      occurredAt = event.toOccurredAt(),
      publishedAt = OffsetDateTime.now().toString(),
      personReference = PersonReference(event.offenderIdDisplay),
    )
      .withAdditionalInformation("nomsNumber", offenderNumber)
      .withAdditionalInformation("reason", receivedReason.reason.name)
      .withAdditionalInformation("details", receivedReason.details)
      .withAdditionalInformation("currentLocation", receivedReason.currentLocation?.name)
      .withAdditionalInformation("prisonId", receivedReason.prisonId)
      .withAdditionalInformation("nomisMovementReasonCode", receivedReason.nomisMovementReason.code)
      .withAdditionalInformation("currentPrisonStatus", receivedReason.currentPrisonStatus?.name)
  }

  private fun toMergedOffenderNumbers(event: PrisonerMergedOffenderEvent): List<HmppsDomainEvent> =
    when (event.type) {
      "MERGE" -> listOf(
        HmppsDomainEvent(
          eventType = "prison-offender-events.prisoner.merged",
          description = "A prisoner has been merged from ${event.offenderIdDisplay} to ${event.previousOffenderIdDisplay}",
          occurredAt = event.toOccurredAt(),
          publishedAt = OffsetDateTime.now().toString(),
          personReference = PersonReference(event.offenderIdDisplay!!),
        )
          .withAdditionalInformation("bookingId", event.bookingId)
          .withAdditionalInformation("nomsNumber", event.offenderIdDisplay)
          .withAdditionalInformation("removedNomsNumber", event.previousOffenderIdDisplay)
          .withAdditionalInformation("reason", "MERGE"),
      )

      else -> emptyList()
    }

  private fun toPrisonerReleased(event: PrisonerDischargedOffenderEvent): HmppsDomainEvent? {
    val offenderNumber = event.offenderIdDisplay
    val releaseReason: ReleaseReason = try {
      releasePrisonerReasonCalculator.calculateReasonForRelease(offenderNumber)
    } catch (e: WebClientResponseException) {
      if (e.statusCode == HttpStatus.NOT_FOUND) {
        // Possibly the offender has been merged since the discharge event
        log.warn("Ignoring release event for {} who no longer exists", offenderNumber)
        return null
      }
      throw e
    }
    if (!releaseReason.hasPrisonerActuallyBeenRelease()) {
      telemetryClient.trackEvent(
        "prison-offender-events.prisoner.not-released",
        asTelemetryMap(event, releaseReason, releaseReason.reason.name),
        null,
      )
      return null
    }
    return HmppsDomainEvent(
      eventType = "prison-offender-events.prisoner.released",
      description = "A prisoner has been released from prison",
      occurredAt = event.toOccurredAt(),
      publishedAt = OffsetDateTime.now().toString(),
      personReference = PersonReference(offenderNumber),
    )
      .withAdditionalInformation("nomsNumber", offenderNumber)
      .withAdditionalInformation("reason", releaseReason.reason.name)
      .withAdditionalInformation("details", releaseReason.details)
      .withAdditionalInformation("currentLocation", releaseReason.currentLocation?.name)
      .withAdditionalInformation("prisonId", releaseReason.prisonId)
      .withAdditionalInformation("nomisMovementReasonCode", releaseReason.nomisMovementReason.code)
      .withAdditionalInformation("currentPrisonStatus", releaseReason.currentPrisonStatus?.name)
  }

  private fun toCaseNotePublished(event: CaseNoteOffenderEvent): HmppsDomainEvent? {
    // SDI-594: If there is no offender id then this means that the case note has actually been removed instead
    // This means that we can ignore this event - will be handled by the offender deletion event instead.
    if (event.offenderIdDisplay == null) {
      log.warn(
        "Ignoring case note published event for case note {} as offender id display is null",
        event.caseNoteId,
      )
      return null
    }

    return HmppsDomainEvent(
      eventType = "prison.case-note.published",
      description = "A prison case note has been created or amended",
      detailUrl = "${offenderEventsProperties.casenotesApiBaseUrl}/case-notes/${event.offenderIdDisplay}/${event.caseNoteId}",
      occurredAt = event.toOccurredAt(),
      publishedAt = OffsetDateTime.now().toString(),
      personReference = PersonReference(event.offenderIdDisplay),
    )
      .withAdditionalInformation("caseNoteId", event.caseNoteId.toString())
      .withAdditionalInformation(
        "caseNoteType",
        "${event.caseNoteType}-${
          event.caseNoteSubType.split("\\W".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()[0]
        }",
      )
      .withAdditionalInformation("type", event.caseNoteType)
      .withAdditionalInformation("subType", event.caseNoteSubType)
  }

  private fun toNonAssociationDetails(event: NonAssociationDetailsOffenderEvent): HmppsDomainEvent = HmppsDomainEvent(
    eventType = "prison-offender-events.prisoner.non-association-detail.changed",
    description = "A prisoner non-association detail record has changed",
    occurredAt = event.toOccurredAt(),
    publishedAt = OffsetDateTime.now().toString(),
    personReference = PersonReference(event.offenderIdDisplay ?: ""),
  )
    .withAdditionalInformation("nomsNumber", event.offenderIdDisplay)
    .withAdditionalInformation("bookingId", event.bookingId)
    .withAdditionalInformation("nonAssociationNomsNumber", event.nsOffenderIdDisplay)
    .withAdditionalInformation("nonAssociationBookingId", event.nsBookingId)
    .withAdditionalInformation("reasonCode", event.reasonCode)
    .withAdditionalInformation("levelCode", event.levelCode)
    .withAdditionalInformation("nonAssociationType", event.nsType)
    .withAdditionalInformation("typeSeq", event.typeSeq)
    .withAdditionalInformation("effectiveDate", event.effectiveDate)
    .withAdditionalInformation("expiryDate", event.expiryDate)
    .withAdditionalInformation("authorisedBy", event.authorisedBy)
    .withAdditionalInformation("comment", event.comment)

  private fun toRestriction(event: RestrictionOffenderEvent): HmppsDomainEvent = HmppsDomainEvent(
    eventType = "prison-offender-events.prisoner.restriction.changed",
    description = "A prisoner restriction record has changed",
    occurredAt = event.toOccurredAt(),
    publishedAt = OffsetDateTime.now().toString(),
    personReference = PersonReference(event.offenderIdDisplay ?: ""),
  )
    .withAdditionalInformation("nomsNumber", event.offenderIdDisplay)
    .withAdditionalInformation("bookingId", event.bookingId)
    .withAdditionalInformation("offenderRestrictionId", event.offenderRestrictionId)
    .withAdditionalInformation("restrictionType", event.restrictionType)
    .withAdditionalInformation("effectiveDate", event.effectiveDate)
    .withAdditionalInformation("expiryDate", event.expiryDate)
    .withAdditionalInformation("authorisedById", event.authorisedById)
    .withAdditionalInformation("enteredById", event.enteredById)

  private fun toPersonRestrictionUpserted(event: PersonRestrictionOffenderEventUpserted): List<HmppsDomainEvent> =
    listOf(
      createPersonRestrictionEvent(event, "prison-offender-events.prisoner.person-restriction.upserted", "A prisoner person restriction record has been created or updated"),
      createPersonRestrictionEvent(event, "prison-offender-events.prisoner.person-restriction.changed", "A prisoner person restriction record has changed"),
    )

  private fun toPersonRestrictionDeleted(event: PersonRestrictionOffenderEventDeleted): List<HmppsDomainEvent> =
    listOf(
      createPersonRestrictionEvent(event, "prison-offender-events.prisoner.person-restriction.deleted", "A prisoner person restriction record has been deleted"),
      createPersonRestrictionEvent(event, "prison-offender-events.prisoner.person-restriction.changed", "A prisoner person restriction record has changed"),
    )

  private fun createPersonRestrictionEvent(event: PersonRestrictionOffenderEvent, eventType: String, description: String) =
    HmppsDomainEvent(
      eventType = eventType,
      description = description,
      occurredAt = event.toOccurredAt(),
      publishedAt = OffsetDateTime.now().toString(),
      personReference = PersonReference(event.offenderIdDisplay ?: ""),
    )
      .withAdditionalInformation("nomsNumber", event.offenderIdDisplay)
      .withAdditionalInformation("contactPersonId", event.contactPersonId)
      .withAdditionalInformation("offenderPersonRestrictionId", event.offenderPersonRestrictionId)
      .withAdditionalInformation("restrictionType", event.restrictionType)
      .withAdditionalInformation("effectiveDate", event.effectiveDate)
      .withAdditionalInformation("expiryDate", event.expiryDate)
      .withAdditionalInformation("authorisedById", event.authorisedById)
      .withAdditionalInformation("enteredById", event.enteredById)
      .withAdditionalInformation("personId", event.personId)

  private fun VisitorRestrictionOffenderEventUpserted.toDomainEvents(): List<HmppsDomainEvent> = listOf(
    this.toDomainEvent(eventType = "prison-offender-events.visitor.restriction.upserted", description = "A prisoner visitor restriction record has been added or amended"),
    this.toDomainEvent(eventType = "prison-offender-events.visitor.restriction.changed", description = "A prisoner visitor restriction record has changed"),
  )

  private fun VisitorRestrictionOffenderEventDeleted.toDomainEvents(): List<HmppsDomainEvent> = listOf(
    this.toDomainEvent(eventType = "prison-offender-events.visitor.restriction.deleted", description = "A prisoner visitor restriction record has been deleted"),
    this.toDomainEvent(eventType = "prison-offender-events.visitor.restriction.changed", description = "A prisoner visitor restriction record has changed"),
  )

  private fun VisitorRestrictionOffenderEvent.toDomainEvent(eventType: String, description: String) =
    HmppsDomainEvent(
      eventType = eventType,
      description = description,
      occurredAt = this.toOccurredAt(),
      publishedAt = OffsetDateTime.now().toString(),
      personReference = PersonReference(HmppsDomainEvent.PersonIdentifier(HmppsDomainEvent.Identifier.PERSON, this.personId.toString())),
    )
      .withAdditionalInformation("personId", this.personId)
      .withAdditionalInformation("restrictionType", this.restrictionType)
      .withAdditionalInformation("effectiveDate", this.effectiveDate)
      .withAdditionalInformation("expiryDate", this.expiryDate)
      .withAdditionalInformation("visitorRestrictionId", this.visitorRestrictionId)
      .withAdditionalInformation("enteredById", this.enteredById)

  private fun toActivityChanged(event: PrisonerActivityUpdateEvent): HmppsDomainEvent =
    HmppsDomainEvent(
      eventType = "prison-offender-events.prisoner.activities-changed",
      description = "A prisoner's activities have been changed",
      occurredAt = event.toOccurredAt(),
      publishedAt = OffsetDateTime.now().toString(),
      personReference = PersonReference(event.offenderIdDisplay),
    )
      .withAdditionalInformation("action", event.action)
      .withAdditionalInformation("prisonId", event.prisonId)
      .withAdditionalInformation("user", event.user)

  private fun toAppointmentChanged(event: PrisonerAppointmentUpdateEvent): HmppsDomainEvent =
    HmppsDomainEvent(
      eventType = "prison-offender-events.prisoner.appointments-changed",
      description = "A prisoner's appointment has been changed",
      occurredAt = event.toOccurredAt(),
      publishedAt = OffsetDateTime.now().toString(),
      personReference = PersonReference(event.offenderIdDisplay),
    )
      .withAdditionalInformation("action", event.action)
      .withAdditionalInformation("prisonId", event.prisonId)
      .withAdditionalInformation("user", event.user)

  private fun toImprisonmentStatusChanged(event: ImprisonmentStatusChangedEvent) =
    if (event.imprisonmentStatusSeq == 0) {
      prisonApiService.getPrisonerNumberForBookingId(event.bookingId).getOrNull()?.let {
        HmppsDomainEvent(
          eventType = "prison-offender-events.prisoner.imprisonment-status-changed",
          description = "A prisoner's imprisonment status has been changed",
          occurredAt = event.toOccurredAt(),
          publishedAt = OffsetDateTime.now().toString(),
          personReference = PersonReference(it),
        ).withAdditionalInformation("bookingId", event.bookingId)
      }
    } else {
      null
    }

  private fun toSentenceDatesChanged(event: SentenceDatesChangedEvent) = HmppsDomainEvent(
    eventType = "prison-offender-events.prisoner.sentence-dates-changed",
    description = "A prisoner's sentence dates have been changed",
    occurredAt = event.toOccurredAt(),
    publishedAt = OffsetDateTime.now().toString(),
    personReference = PersonReference(event.offenderIdDisplay),
  )
    .withAdditionalInformation("nomsNumber", event.offenderIdDisplay)
    .withAdditionalInformation("bookingId", event.bookingId)
    .withAdditionalInformation("sentenceCalculationId", event.sentenceCalculationId)

  fun sendEvents(events: List<HmppsDomainEvent>) {
    events.forEach {
      sendEvent(it)
      telemetryClient.trackEvent(it.eventType, it.asTelemetryMap(), null)
    }
  }
  fun sendEvent(payload: HmppsDomainEvent) {
    try {
      hmppsEventsTopicSnsClient.publish(
        PublishRequest.builder()
          .topicArn(topicArn)
          .message(objectMapper.writeValueAsString(payload))
          .messageAttributes(payload.asMetadataMap()).build(),
      ).get()
    } catch (e: JsonProcessingException) {
      log.error("Failed to convert payload {} to json", payload)
    }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

private fun HmppsDomainEvent.toList() = listOf(this)
private fun HmppsDomainEvent?.toListOrEmptyWhenNull() = this?.toList() ?: emptyList()

private fun OffenderContactEventInserted.toDomainEvent(): HmppsDomainEvent? = this.toDomainEvent(
  eventType = "prison-offender-events.prisoner.contact-added",
  description = "A contact has been added to a prisoner",
)

private fun OffenderContactEventDeleted.toDomainEvent() = this.toDomainEvent(
  eventType = "prison-offender-events.prisoner.contact-removed",
  description = "A contact for a prisoner has been removed",
)

private fun OffenderContactEventUpdated.toDomainEvent() = this.toDomainEvent(
  eventType = if (this.approvedVisitor) "prison-offender-events.prisoner.contact-approved" else "prison-offender-events.prisoner.contact-unapproved",
  description = "A contact for a prisoner has been ${if (this.approvedVisitor) "approved" else "unapproved"}",
)

private fun OffenderContactEvent.toDomainEvent(eventType: String, description: String) =
  if (personId != null) {
    HmppsDomainEvent(
      eventType = eventType,
      description = description,
      occurredAt = toOccurredAt(),
      publishedAt = OffsetDateTime.now().toString(),
      personReference = PersonReference(offenderIdDisplay),
    ).withContactAdditionalInformation(this)
  } else {
    null
  }

private fun HmppsDomainEvent.withContactAdditionalInformation(xtagEvent: OffenderContactEvent): HmppsDomainEvent =
  this.withAdditionalInformation("nomsNumber", xtagEvent.offenderIdDisplay)
    .withAdditionalInformation("bookingId", xtagEvent.bookingId)
    .withAdditionalInformation("personId", xtagEvent.personId)
    .withAdditionalInformation("contactId", xtagEvent.contactId)
    .withAdditionalInformation("approvedVisitor", xtagEvent.approvedVisitor)
    .withAdditionalInformation("username", xtagEvent.username)

private fun PrisonerBookingMovedOffenderEvent.toDomainEvent(): HmppsDomainEvent? = this.takeIf { offenderIdDisplay != previousOffenderIdDisplay }?.let {
  HmppsDomainEvent(
    eventType = "prison-offender-events.prisoner.booking.moved",
    description = "a NOMIS booking has moved between prisoners",
    occurredAt = toOccurredAt(),
    publishedAt = OffsetDateTime.now().toString(),
    personReference = PersonReference(offenderIdDisplay),
  )
    .withAdditionalInformation("bookingId", bookingId)
    .withAdditionalInformation("movedToNomsNumber", offenderIdDisplay)
    .withAdditionalInformation("movedFromNomsNumber", previousOffenderIdDisplay)
}
