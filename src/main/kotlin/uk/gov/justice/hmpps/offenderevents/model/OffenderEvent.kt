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
}

class CaseNoteOffenderEvent(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String? = null,
  val caseNoteId: Long,
  val caseNoteType: String,
  val caseNoteSubType: String,
) : OffenderEvent(eventDatetime = eventDatetime, offenderIdDisplay = offenderIdDisplay) {
  companion object
}

class CellMoveOffenderEvent(
  eventDatetime: LocalDateTime,
  override val offenderIdDisplay: String,
  val livingUnitId: Long,
  val bedAssignmentSeq: Int,
  val bookingId: String,
) : OffenderEvent(eventDatetime = eventDatetime, offenderIdDisplay = offenderIdDisplay) {
  companion object
}

class PrisonerReceivedOffenderEvent(
  eventDatetime: LocalDateTime,
  override val offenderIdDisplay: String,
) : OffenderEvent(eventDatetime = eventDatetime, offenderIdDisplay = offenderIdDisplay) {
  companion object
}

class PrisonerDischargedOffenderEvent(
  eventDatetime: LocalDateTime,
  override val offenderIdDisplay: String,
) : OffenderEvent(eventDatetime = eventDatetime, offenderIdDisplay = offenderIdDisplay) {
  companion object
}

class PrisonerMergedOffenderEvent(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String? = null,
  val previousOffenderIdDisplay: String? = null,
  val type: String,
  val bookingId: Long,
) : OffenderEvent(eventDatetime = eventDatetime, offenderIdDisplay = offenderIdDisplay) {
  companion object
}

class PrisonerBookingMovedOffenderEvent(
  eventDatetime: LocalDateTime,
  override val offenderIdDisplay: String,
  val previousOffenderIdDisplay: String,
  val bookingId: Long,
) : OffenderEvent(eventDatetime = eventDatetime, offenderIdDisplay = offenderIdDisplay) {
  companion object
}

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
) {
  companion object
}
class RestrictionOffenderEvent(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String? = null,

  val bookingId: Long? = null,
  val offenderRestrictionId: Long?,
  val restrictionType: String?,
  val effectiveDate: LocalDate?,
  val expiryDate: LocalDate?,
  val authorisedById: Long?,
  val enteredById: Long?,
) : OffenderEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
) {
  companion object
}

abstract class PersonRestrictionOffenderEvent(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String? = null,

  val contactPersonId: Long?,
  val offenderPersonRestrictionId: Long?,
  val personId: Long?,
  val restrictionType: String?,
  val effectiveDate: LocalDate?,
  val expiryDate: LocalDate?,
  val authorisedById: Long?,
  val enteredById: Long?,
) : OffenderEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
)

class PersonRestrictionOffenderEventUpserted(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String? = null,
  contactPersonId: Long?,
  personId: Long?,
  offenderPersonRestrictionId: Long?,
  restrictionType: String?,
  effectiveDate: LocalDate?,
  expiryDate: LocalDate?,
  authorisedById: Long?,
  enteredById: Long?,
) : PersonRestrictionOffenderEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
  contactPersonId = contactPersonId,
  offenderPersonRestrictionId = offenderPersonRestrictionId,
  personId = personId,
  restrictionType = restrictionType,
  effectiveDate = effectiveDate,
  expiryDate = expiryDate,
  authorisedById = authorisedById,
  enteredById = enteredById,
) {
  companion object
}

class PersonRestrictionOffenderEventDeleted(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String? = null,
  contactPersonId: Long?,
  personId: Long?,
  offenderPersonRestrictionId: Long?,
  restrictionType: String?,
  effectiveDate: LocalDate?,
  expiryDate: LocalDate?,
  authorisedById: Long?,
  enteredById: Long?,
) : PersonRestrictionOffenderEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
  contactPersonId = contactPersonId,
  offenderPersonRestrictionId = offenderPersonRestrictionId,
  personId = personId,
  restrictionType = restrictionType,
  effectiveDate = effectiveDate,
  expiryDate = expiryDate,
  authorisedById = authorisedById,
  enteredById = enteredById,
) {
  companion object
}

@JsonInclude(JsonInclude.Include.NON_NULL)
class VisitorRestrictionOffenderEventUpserted(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String? = null,
  personId: Long?,
  restrictionType: String?,
  effectiveDate: LocalDate?,
  expiryDate: LocalDate?,
  visitorRestrictionId: Long?,
  enteredById: Long?,
) : VisitorRestrictionOffenderEvent(
  personId = personId,
  restrictionType = restrictionType,
  effectiveDate = effectiveDate,
  expiryDate = expiryDate,
  visitorRestrictionId = visitorRestrictionId,
  enteredById = enteredById,
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
) {
  companion object
}

@JsonInclude(JsonInclude.Include.NON_NULL)
class VisitorRestrictionOffenderEventDeleted(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String? = null,
  personId: Long?,
  restrictionType: String?,
  effectiveDate: LocalDate?,
  expiryDate: LocalDate?,
  visitorRestrictionId: Long?,
  enteredById: Long?,
) : VisitorRestrictionOffenderEvent(
  personId = personId,
  restrictionType = restrictionType,
  effectiveDate = effectiveDate,
  expiryDate = expiryDate,
  visitorRestrictionId = visitorRestrictionId,
  enteredById = enteredById,
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
) {
  companion object
}

@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class VisitorRestrictionOffenderEvent(
  eventDatetime: LocalDateTime,
  offenderIdDisplay: String? = null,
  val personId: Long?,
  val restrictionType: String?,
  val effectiveDate: LocalDate?,
  val expiryDate: LocalDate?,
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
) {
  companion object
}

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
) {
  companion object
}

@JsonInclude(JsonInclude.Include.NON_NULL)
class ImprisonmentStatusChangedEvent(
  eventDatetime: LocalDateTime,
  val bookingId: Long,
  val imprisonmentStatusSeq: Int,
) : OffenderEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = null,
) {
  companion object
}

@JsonInclude(JsonInclude.Include.NON_NULL)
class SentenceDatesChangedEvent(
  eventDatetime: LocalDateTime,
  override val offenderIdDisplay: String,
  val bookingId: Long,
  val sentenceCalculationId: Long,
) : OffenderEvent(
  eventDatetime = eventDatetime,
  offenderIdDisplay = offenderIdDisplay,
) {
  companion object
}

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
) {
  companion object
}

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
) {
  companion object
}

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
) {
  companion object
}
