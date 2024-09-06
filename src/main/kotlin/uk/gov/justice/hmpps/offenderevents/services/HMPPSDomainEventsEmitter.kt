package uk.gov.justice.hmpps.offenderevents.services

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  private final val hmppsEventsTopicSnsClient: SnsAsyncClient
  private final val topicArn: String

  init {
    val hmppsEventTopic = hmppsQueueService.findByTopicId("hmppseventtopic")
    topicArn = hmppsEventTopic!!.arn
    hmppsEventsTopicSnsClient = hmppsEventTopic.snsClient
  }

  fun convertAndSendWhenSignificant(event: String, message: String) {
    when (event) {
      "VISITOR_RESTRICTION-UPSERTED" -> VisitorRestrictionOffenderEventUpserted.toDomainEvents(message.fromJson())
      "VISITOR_RESTRICTION-DELETED" -> VisitorRestrictionOffenderEventDeleted.toDomainEvents(message.fromJson())
      "OFFENDER_CONTACT-INSERTED" -> OffenderContactEventInserted.toDomainEvents(message.fromJson())
      "OFFENDER_CONTACT-UPDATED" -> OffenderContactEventUpdated.toDomainEvents(message.fromJson())
      "OFFENDER_CONTACT-DELETED" -> OffenderContactEventDeleted.toDomainEvents(message.fromJson())
      "OFFENDER_BOOKING-REASSIGNED" -> PrisonerBookingMovedOffenderEvent.toDomainEvents(message.fromJson())
      "BOOKING_NUMBER-CHANGED" -> PrisonerMergedOffenderEvent.toDomainEvents(message.fromJson())
      "OFFENDER_CASE_NOTES-DELETED" -> CaseNoteOffenderEvent.toDomainEvents(message.fromJson())
      "OFFENDER_CASE_NOTES-INSERTED" -> CaseNoteOffenderEvent.toDomainEvents(message.fromJson())
      "OFFENDER_CASE_NOTES-UPDATED" -> CaseNoteOffenderEvent.toDomainEvents(message.fromJson())
      "BED_ASSIGNMENT_HISTORY-INSERTED" -> CellMoveOffenderEvent.toDomainEvents(message.fromJson())
      "OFFENDER_MOVEMENT-RECEPTION" -> PrisonerReceivedOffenderEvent.toDomainEvents(message.fromJson())
      "OFFENDER_MOVEMENT-DISCHARGE" -> PrisonerDischargedOffenderEvent.toDomainEvents(message.fromJson())
      "NON_ASSOCIATION_DETAIL-UPSERTED" -> NonAssociationDetailsOffenderEvent.toDomainEvents(message.fromJson())
      "NON_ASSOCIATION_DETAIL-DELETED" -> NonAssociationDetailsOffenderEvent.toDomainEvents(message.fromJson())
      "RESTRICTION-UPSERTED" -> RestrictionOffenderEvent.toDomainEvents(message.fromJson())
      "RESTRICTION-DELETED" -> RestrictionOffenderEvent.toDomainEvents(message.fromJson())
      "PERSON_RESTRICTION-UPSERTED" -> PersonRestrictionOffenderEventUpserted.toDomainEvents(message.fromJson())
      "PERSON_RESTRICTION-DELETED" -> PersonRestrictionOffenderEventDeleted.toDomainEvents(message.fromJson())
      "PRISONER_ACTIVITY-UPDATE" -> PrisonerActivityUpdateEvent.toDomainEvents(message.fromJson())
      "PRISONER_APPOINTMENT-UPDATE" -> PrisonerAppointmentUpdateEvent.toDomainEvents(message.fromJson())
      "IMPRISONMENT_STATUS-CHANGED" -> ImprisonmentStatusChangedEvent.toDomainEvents(message.fromJson())
      "SENTENCE_DATES-CHANGED" -> SentenceDatesChangedEvent.toDomainEvents(message.fromJson())
      else -> emptyList()
    }.also {
      sendEvents(it)
    }
  }

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)

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

  private fun CellMoveOffenderEvent.toDomainEvent(): HmppsDomainEvent = HmppsDomainEvent(
    eventType = "prison-offender-events.prisoner.cell.move",
    description = "A prisoner has been moved to a different cell",
    occurredAt = this.toOccurredAt(),
    publishedAt = OffsetDateTime.now().toString(),
    personReference = PersonReference(this.offenderIdDisplay),
  )
    .withAdditionalInformation("nomsNumber", this.offenderIdDisplay)
    .withAdditionalInformation("livingUnitId", this.livingUnitId)
    .withAdditionalInformation("bedAssignmentSeq", this.bedAssignmentSeq)
    .withAdditionalInformation("bookingId", this.bookingId)

  private fun PrisonerReceivedOffenderEvent.toDomainEvent(): HmppsDomainEvent? {
    val offenderNumber = this.offenderIdDisplay
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
          this,
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
      occurredAt = this.toOccurredAt(),
      publishedAt = OffsetDateTime.now().toString(),
      personReference = PersonReference(this.offenderIdDisplay),
    )
      .withAdditionalInformation("nomsNumber", offenderNumber)
      .withAdditionalInformation("reason", receivedReason.reason.name)
      .withAdditionalInformation("details", receivedReason.details)
      .withAdditionalInformation("currentLocation", receivedReason.currentLocation?.name)
      .withAdditionalInformation("prisonId", receivedReason.prisonId)
      .withAdditionalInformation("nomisMovementReasonCode", receivedReason.nomisMovementReason.code)
      .withAdditionalInformation("currentPrisonStatus", receivedReason.currentPrisonStatus?.name)
  }

  private fun PrisonerDischargedOffenderEvent.toDomainEvent(): HmppsDomainEvent? {
    val offenderNumber = this.offenderIdDisplay
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
        asTelemetryMap(this, releaseReason, releaseReason.reason.name),
        null,
      )
      return null
    }
    return HmppsDomainEvent(
      eventType = "prison-offender-events.prisoner.released",
      description = "A prisoner has been released from prison",
      occurredAt = this.toOccurredAt(),
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

  private fun CaseNoteOffenderEvent.toDomainEvent(): HmppsDomainEvent? {
    // SDI-594: If there is no offender id then this means that the case note has actually been removed instead
    // This means that we can ignore this event - will be handled by the offender deletion event instead.
    if (this.offenderIdDisplay == null) {
      log.warn(
        "Ignoring case note published event for case note {} as offender id display is null",
        this.caseNoteId,
      )
      return null
    }

    return HmppsDomainEvent(
      eventType = "prison.case-note.published",
      description = "A prison case note has been created or amended",
      detailUrl = "${offenderEventsProperties.casenotesApiBaseUrl}/case-notes/${this.offenderIdDisplay}/${this.caseNoteId}",
      occurredAt = this.toOccurredAt(),
      publishedAt = OffsetDateTime.now().toString(),
      personReference = PersonReference(this.offenderIdDisplay),
    )
      .withAdditionalInformation("caseNoteId", this.caseNoteId.toString())
      .withAdditionalInformation(
        "caseNoteType",
        "${this.caseNoteType}-${
          this.caseNoteSubType.split("\\W".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()[0]
        }",
      )
      .withAdditionalInformation("type", this.caseNoteType)
      .withAdditionalInformation("subType", this.caseNoteSubType)
  }

  private fun NonAssociationDetailsOffenderEvent.toDomainEvent(): HmppsDomainEvent = HmppsDomainEvent(
    eventType = "prison-offender-events.prisoner.non-association-detail.changed",
    description = "A prisoner non-association detail record has changed",
    occurredAt = this.toOccurredAt(),
    publishedAt = OffsetDateTime.now().toString(),
    personReference = PersonReference(this.offenderIdDisplay ?: ""),
  )
    .withAdditionalInformation("nomsNumber", this.offenderIdDisplay)
    .withAdditionalInformation("bookingId", this.bookingId)
    .withAdditionalInformation("nonAssociationNomsNumber", this.nsOffenderIdDisplay)
    .withAdditionalInformation("nonAssociationBookingId", this.nsBookingId)
    .withAdditionalInformation("reasonCode", this.reasonCode)
    .withAdditionalInformation("levelCode", this.levelCode)
    .withAdditionalInformation("nonAssociationType", this.nsType)
    .withAdditionalInformation("typeSeq", this.typeSeq)
    .withAdditionalInformation("effectiveDate", this.effectiveDate)
    .withAdditionalInformation("expiryDate", this.expiryDate)
    .withAdditionalInformation("authorisedBy", this.authorisedBy)
    .withAdditionalInformation("comment", this.comment)

  private fun RestrictionOffenderEvent.toDomainEvent(): HmppsDomainEvent = HmppsDomainEvent(
    eventType = "prison-offender-events.prisoner.restriction.changed",
    description = "A prisoner restriction record has changed",
    occurredAt = this.toOccurredAt(),
    publishedAt = OffsetDateTime.now().toString(),
    personReference = PersonReference(this.offenderIdDisplay ?: ""),
  )
    .withAdditionalInformation("nomsNumber", this.offenderIdDisplay)
    .withAdditionalInformation("bookingId", this.bookingId)
    .withAdditionalInformation("offenderRestrictionId", this.offenderRestrictionId)
    .withAdditionalInformation("restrictionType", this.restrictionType)
    .withAdditionalInformation("effectiveDate", this.effectiveDate)
    .withAdditionalInformation("expiryDate", this.expiryDate)
    .withAdditionalInformation("authorisedById", this.authorisedById)
    .withAdditionalInformation("enteredById", this.enteredById)

  private fun PersonRestrictionOffenderEventUpserted.toDomainEvent(): List<HmppsDomainEvent> =
    listOf(
      createPersonRestrictionEvent(this, "prison-offender-events.prisoner.person-restriction.upserted", "A prisoner person restriction record has been created or updated"),
      createPersonRestrictionEvent(this, "prison-offender-events.prisoner.person-restriction.changed", "A prisoner person restriction record has changed"),
    )

  private fun PersonRestrictionOffenderEventDeleted.toDomainEvent(): List<HmppsDomainEvent> =
    listOf(
      createPersonRestrictionEvent(this, "prison-offender-events.prisoner.person-restriction.deleted", "A prisoner person restriction record has been deleted"),
      createPersonRestrictionEvent(this, "prison-offender-events.prisoner.person-restriction.changed", "A prisoner person restriction record has changed"),
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

  private fun PrisonerActivityUpdateEvent.toDomainEvent(): HmppsDomainEvent =
    HmppsDomainEvent(
      eventType = "prison-offender-events.prisoner.activities-changed",
      description = "A prisoner's activities have been changed",
      occurredAt = this.toOccurredAt(),
      publishedAt = OffsetDateTime.now().toString(),
      personReference = PersonReference(this.offenderIdDisplay),
    )
      .withAdditionalInformation("action", this.action)
      .withAdditionalInformation("prisonId", this.prisonId)
      .withAdditionalInformation("user", this.user)

  private fun PrisonerAppointmentUpdateEvent.toDomainEvent(): HmppsDomainEvent =
    HmppsDomainEvent(
      eventType = "prison-offender-events.prisoner.appointments-changed",
      description = "A prisoner's appointment has been changed",
      occurredAt = this.toOccurredAt(),
      publishedAt = OffsetDateTime.now().toString(),
      personReference = PersonReference(this.offenderIdDisplay),
    )
      .withAdditionalInformation("action", this.action)
      .withAdditionalInformation("prisonId", this.prisonId)
      .withAdditionalInformation("user", this.user)

  private fun ImprisonmentStatusChangedEvent.toDomainEvent() =
    if (this.imprisonmentStatusSeq == 0) {
      prisonApiService.getPrisonerNumberForBookingId(this.bookingId).getOrNull()?.let {
        HmppsDomainEvent(
          eventType = "prison-offender-events.prisoner.imprisonment-status-changed",
          description = "A prisoner's imprisonment status has been changed",
          occurredAt = this.toOccurredAt(),
          publishedAt = OffsetDateTime.now().toString(),
          personReference = PersonReference(it),
        ).withAdditionalInformation("bookingId", this.bookingId)
      }
    } else {
      null
    }

  private fun SentenceDatesChangedEvent.toDomainEvent() = HmppsDomainEvent(
    eventType = "prison-offender-events.prisoner.sentence-dates-changed",
    description = "A prisoner's sentence dates have been changed",
    occurredAt = this.toOccurredAt(),
    publishedAt = OffsetDateTime.now().toString(),
    personReference = PersonReference(this.offenderIdDisplay),
  )
    .withAdditionalInformation("nomsNumber", this.offenderIdDisplay)
    .withAdditionalInformation("bookingId", this.bookingId)
    .withAdditionalInformation("sentenceCalculationId", this.sentenceCalculationId)

  private fun SentenceDatesChangedEvent.Companion.toDomainEvents(event: SentenceDatesChangedEvent): List<HmppsDomainEvent> =
    event.toDomainEvent().toListOrEmptyWhenNull()

  private fun ImprisonmentStatusChangedEvent.Companion.toDomainEvents(event: ImprisonmentStatusChangedEvent): List<HmppsDomainEvent> =
    event.toDomainEvent().toListOrEmptyWhenNull()

  private fun PrisonerAppointmentUpdateEvent.Companion.toDomainEvents(event: PrisonerAppointmentUpdateEvent): List<HmppsDomainEvent> =
    event.toDomainEvent().toListOrEmptyWhenNull()

  private fun PrisonerActivityUpdateEvent.Companion.toDomainEvents(event: PrisonerActivityUpdateEvent): List<HmppsDomainEvent> =
    event.toDomainEvent().toListOrEmptyWhenNull()

  private fun PersonRestrictionOffenderEventDeleted.Companion.toDomainEvents(event: PersonRestrictionOffenderEventDeleted): List<HmppsDomainEvent> =
    event.toDomainEvent()

  private fun PersonRestrictionOffenderEventUpserted.Companion.toDomainEvents(event: PersonRestrictionOffenderEventUpserted): List<HmppsDomainEvent> =
    event.toDomainEvent()

  private fun RestrictionOffenderEvent.Companion.toDomainEvents(event: RestrictionOffenderEvent): List<HmppsDomainEvent> =
    event.toDomainEvent().toListOrEmptyWhenNull()
  private fun NonAssociationDetailsOffenderEvent.Companion.toDomainEvents(event: NonAssociationDetailsOffenderEvent): List<HmppsDomainEvent> =
    event.toDomainEvent().toListOrEmptyWhenNull()

  private fun PrisonerDischargedOffenderEvent.Companion.toDomainEvents(event: PrisonerDischargedOffenderEvent): List<HmppsDomainEvent> =
    event.toDomainEvent().toListOrEmptyWhenNull()

  private fun PrisonerReceivedOffenderEvent.Companion.toDomainEvents(event: PrisonerReceivedOffenderEvent): List<HmppsDomainEvent> =
    event.toDomainEvent().toListOrEmptyWhenNull()

  private fun CellMoveOffenderEvent.Companion.toDomainEvents(event: CellMoveOffenderEvent): List<HmppsDomainEvent> =
    event.toDomainEvent().toListOrEmptyWhenNull()

  private fun CaseNoteOffenderEvent.Companion.toDomainEvents(event: CaseNoteOffenderEvent): List<HmppsDomainEvent> =
    event.toDomainEvent().toListOrEmptyWhenNull()
  fun OffenderContactEventInserted.Companion.toDomainEvents(message: OffenderContactEventInserted): List<HmppsDomainEvent> =
    message.toDomainEvent().toListOrEmptyWhenNull()

  fun OffenderContactEventDeleted.Companion.toDomainEvents(fromJson: OffenderContactEventDeleted): List<HmppsDomainEvent> =
    fromJson.toDomainEvent().toListOrEmptyWhenNull()

  fun OffenderContactEventUpdated.Companion.toDomainEvents(fromJson: OffenderContactEventUpdated): List<HmppsDomainEvent> =
    fromJson.toDomainEvent().toListOrEmptyWhenNull()

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

  private fun PrisonerBookingMovedOffenderEvent.Companion.toDomainEvents(message: PrisonerBookingMovedOffenderEvent): List<HmppsDomainEvent> =
    message.toDomainEvent().toListOrEmptyWhenNull()

  private fun PrisonerBookingMovedOffenderEvent.toDomainEvent(): HmppsDomainEvent? =
    this.takeIf { offenderIdDisplay != previousOffenderIdDisplay }?.let {
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

  private fun VisitorRestrictionOffenderEventUpserted.Companion.toDomainEvents(message: VisitorRestrictionOffenderEventUpserted): List<HmppsDomainEvent> =
    message.toDomainEvents()

  private fun VisitorRestrictionOffenderEventDeleted.Companion.toDomainEvents(fromJson: VisitorRestrictionOffenderEventDeleted): List<HmppsDomainEvent> =
    fromJson.toDomainEvents()

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

  private fun PrisonerMergedOffenderEvent.Companion.toDomainEvents(event: PrisonerMergedOffenderEvent): List<HmppsDomainEvent> =
    event.toDomainEvents()

  private fun PrisonerMergedOffenderEvent.toDomainEvents(): List<HmppsDomainEvent> =
    when (this.type) {
      "MERGE" -> listOf(
        HmppsDomainEvent(
          eventType = "prison-offender-events.prisoner.merged",
          description = "A prisoner has been merged from ${this.offenderIdDisplay} to ${this.previousOffenderIdDisplay}",
          occurredAt = this.toOccurredAt(),
          publishedAt = OffsetDateTime.now().toString(),
          personReference = PersonReference(this.offenderIdDisplay!!),
        )
          .withAdditionalInformation("bookingId", this.bookingId)
          .withAdditionalInformation("nomsNumber", this.offenderIdDisplay)
          .withAdditionalInformation("removedNomsNumber", this.previousOffenderIdDisplay)
          .withAdditionalInformation("reason", "MERGE"),
      )

      else -> emptyList()
    }
}

private fun HmppsDomainEvent.toList() = listOf(this)
private fun HmppsDomainEvent?.toListOrEmptyWhenNull() = this?.toList() ?: emptyList()
