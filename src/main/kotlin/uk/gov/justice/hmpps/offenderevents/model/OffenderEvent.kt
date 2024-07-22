package uk.gov.justice.hmpps.offenderevents.model

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME

@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class OffenderEvent(
  val eventDatetime: LocalDateTime,
  open val offenderIdDisplay: String?,
) {
  internal fun toOccurredAt(): String =
    eventDatetime.atZone(ZoneId.of("Europe/London")).toOffsetDateTime().format(ISO_OFFSET_DATE_TIME)

  companion object {
    val eventMappings = mapOf(
      "OFFENDER_CASE_NOTES-DELETED" to CaseNoteOffenderEvent::class.java,
      "OFFENDER_CASE_NOTES-INSERTED" to CaseNoteOffenderEvent::class.java,
      "OFFENDER_CASE_NOTES-UPDATED" to CaseNoteOffenderEvent::class.java,
      "BED_ASSIGNMENT_HISTORY-INSERTED" to CellMoveOffenderEvent::class.java,
      "OFFENDER_MOVEMENT-RECEPTION" to PrisonerReceivedOffenderEvent::class.java,
      "OFFENDER_MOVEMENT-DISCHARGE" to PrisonerDischargedOffenderEvent::class.java,
      "BOOKING_NUMBER-CHANGED" to PrisonerMergedOffenderEvent::class.java,
      "OFFENDER_BOOKING-REASSIGNED" to PrisonerBookingMovedOffenderEvent::class.java,
      "NON_ASSOCIATION_DETAIL-UPSERTED" to NonAssociationDetailsOffenderEvent::class.java,
      "NON_ASSOCIATION_DETAIL-DELETED" to NonAssociationDetailsOffenderEvent::class.java,
      "RESTRICTION-UPSERTED" to RestrictionOffenderEvent::class.java,
      "RESTRICTION-DELETED" to RestrictionOffenderEvent::class.java,
      "PERSON_RESTRICTION-UPSERTED" to PersonRestrictionOffenderEventUpserted::class.java,
      "PERSON_RESTRICTION-DELETED" to PersonRestrictionOffenderEventDeleted::class.java,
      "VISITOR_RESTRICTION-UPSERTED" to VisitorRestrictionOffenderEvent::class.java,
      "VISITOR_RESTRICTION-DELETED" to VisitorRestrictionOffenderEvent::class.java,
      "PRISONER_ACTIVITY-UPDATE" to PrisonerActivityUpdateEvent::class.java,
      "PRISONER_APPOINTMENT-UPDATE" to PrisonerAppointmentUpdateEvent::class.java,
      "IMPRISONMENT_STATUS-CHANGED" to ImprisonmentStatusChangedEvent::class.java,
      "SENTENCE_DATES-CHANGED" to SentenceDatesChangedEvent::class.java,
      "OFFENDER_CONTACT-INSERTED" to OffenderContactEventInserted::class.java,
      "OFFENDER_CONTACT-UPDATED" to OffenderContactEventUpdated::class.java,
      "OFFENDER_CONTACT-DELETED" to OffenderContactEventDeleted::class.java,
    )
  }
}

class CaseNoteOffenderEvent(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String? = null,
  val caseNoteId: Long,
  val caseNoteType: String,
  val caseNoteSubType: String,
) : OffenderEvent(eventDatetime = eventDatetime, offenderIdDisplay = offenderIdDisplay)

class CellMoveOffenderEvent(
  eventDatetime: LocalDateTime,
  override val offenderIdDisplay: String,
  val livingUnitId: Long,
  val bedAssignmentSeq: Int,
  val bookingId: String,
) : OffenderEvent(eventDatetime = eventDatetime, offenderIdDisplay = offenderIdDisplay)

class PrisonerReceivedOffenderEvent(
  eventDatetime: LocalDateTime,
  override val offenderIdDisplay: String,
) : OffenderEvent(eventDatetime = eventDatetime, offenderIdDisplay = offenderIdDisplay)

class PrisonerDischargedOffenderEvent(
  eventDatetime: LocalDateTime,
  override val offenderIdDisplay: String,
) : OffenderEvent(eventDatetime = eventDatetime, offenderIdDisplay = offenderIdDisplay)

class PrisonerMergedOffenderEvent(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String? = null,
  val bookingId: Long,
) : OffenderEvent(eventDatetime = eventDatetime, offenderIdDisplay = offenderIdDisplay)

class PrisonerBookingMovedOffenderEvent(
  eventDatetime: LocalDateTime,
  override val offenderIdDisplay: String,
  val previousOffenderIdDisplay: String,
  val bookingId: Long,
) : OffenderEvent(eventDatetime = eventDatetime, offenderIdDisplay = offenderIdDisplay)

class NonAssociationDetailsOffenderEvent(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String?,

  val bookingId: Long?,
  val nsOffenderIdDisplay: String?,
  val nsBookingId: Long?,
  val reasonCode: String?,
  val levelCode: String? = null,
  val nsType: String?,
  val typeSeq: Int?,
  val effectiveDate: LocalDate?,
  val expiryDate: LocalDate?,
  val authorisedBy: String?,
  val comment: String?,
) : OffenderEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
)

class RestrictionOffenderEvent(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String? = null,

  val bookingId: Long? = null,
  val offenderRestrictionId: Long?,
  val restrictionType: String?,
  val effectiveDate: LocalDate?,
  val expiryDate: LocalDate?,
  val comment: String?,
  val authorisedById: Long?,
  val enteredById: Long?,
) : OffenderEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
)

abstract class PersonRestrictionOffenderEvent(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String? = null,

  val contactPersonId: Long?,
  val offenderPersonRestrictionId: Long?,
  val restrictionType: String?,
  val effectiveDate: LocalDate?,
  val expiryDate: LocalDate?,
  val authorisedById: Long?,
  val comment: String?,
  val enteredById: Long?,
) : OffenderEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
)

class PersonRestrictionOffenderEventUpserted(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String? = null,
  contactPersonId: Long?,
  offenderPersonRestrictionId: Long?,
  restrictionType: String?,
  effectiveDate: LocalDate?,
  expiryDate: LocalDate?,
  authorisedById: Long?,
  comment: String?,
  enteredById: Long?,
) : PersonRestrictionOffenderEvent(
  eventDatetime,
  offenderIdDisplay,
  contactPersonId,
  offenderPersonRestrictionId,
  restrictionType,
  effectiveDate,
  expiryDate,
  authorisedById,
  comment,
  enteredById,
)

class PersonRestrictionOffenderEventDeleted(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String? = null,
  contactPersonId: Long?,
  offenderPersonRestrictionId: Long?,
  restrictionType: String?,
  effectiveDate: LocalDate?,
  expiryDate: LocalDate?,
  authorisedById: Long?,
  comment: String?,
  enteredById: Long?,
) : PersonRestrictionOffenderEvent(
  eventDatetime,
  offenderIdDisplay,
  contactPersonId,
  offenderPersonRestrictionId,
  restrictionType,
  effectiveDate,
  expiryDate,
  authorisedById,
  comment,
  enteredById,
)

class VisitorRestrictionOffenderEvent(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String? = null,

  val personId: Long?,
  val restrictionType: String?,
  val effectiveDate: LocalDate?,
  val expiryDate: LocalDate?,
  val comment: String?,
  val visitorRestrictionId: Long?,
  val enteredById: Long?,
) : OffenderEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
class PrisonerActivityUpdateEvent(
  eventDatetime: LocalDateTime,
  override val offenderIdDisplay: String,

  val prisonId: String,
  val action: String,
  val user: String,
) : OffenderEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
class PrisonerAppointmentUpdateEvent(
  eventDatetime: LocalDateTime,
  override val offenderIdDisplay: String,

  val prisonId: String,
  val action: String,
  val user: String,
) : OffenderEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
class ImprisonmentStatusChangedEvent(
  eventDatetime: LocalDateTime,
  val bookingId: Long,
  val imprisonmentStatusSeq: Int,
) : OffenderEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
class SentenceDatesChangedEvent(
  eventDatetime: LocalDateTime,
  override val offenderIdDisplay: String,
  val bookingId: Long,
  val sentenceCalculationId: Long,
) : OffenderEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class OffenderContactEvent(
  eventDatetime: LocalDateTime,
  override val offenderIdDisplay: String,
  val personId: Long?,
  val contactId: Long,
  val approvedVisitor: Boolean,
  val username: String?,
  val bookingId: Long,
) : OffenderEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
)

class OffenderContactEventInserted(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String,
  personId: Long?,
  contactId: Long,
  approvedVisitor: Boolean,
  username: String?,
  bookingId: Long,
) : OffenderContactEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
  personId = personId,
  contactId = contactId,
  approvedVisitor = approvedVisitor,
  username = username,
  bookingId = bookingId,
)

class OffenderContactEventUpdated(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String,
  personId: Long?,
  contactId: Long,
  approvedVisitor: Boolean,
  username: String?,
  bookingId: Long,
) : OffenderContactEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
  personId = personId,
  contactId = contactId,
  approvedVisitor = approvedVisitor,
  username = username,
  bookingId = bookingId,
)

class OffenderContactEventDeleted(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String,
  personId: Long?,
  contactId: Long,
  approvedVisitor: Boolean,
  username: String?,
  bookingId: Long,
) : OffenderContactEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
  personId = personId,
  contactId = contactId,
  approvedVisitor = approvedVisitor,
  username = username,
  bookingId = bookingId,
)
