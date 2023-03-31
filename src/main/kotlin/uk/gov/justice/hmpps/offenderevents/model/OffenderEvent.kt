package uk.gov.justice.hmpps.offenderevents.model

import com.fasterxml.jackson.annotation.JsonInclude
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
