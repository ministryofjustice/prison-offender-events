package uk.gov.justice.hmpps.offenderevents.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.time.LocalDate
import java.util.Optional

internal enum class LegalStatus {
  RECALL, DEAD, INDETERMINATE_SENTENCE, SENTENCED, CONVICTED_UNSENTENCED, CIVIL_PRISONER, IMMIGRATION_DETAINEE, REMAND, UNKNOWN, OTHER
}

internal enum class MovementType {
  TEMPORARY_ABSENCE, COURT, ADMISSION, RELEASED, TRANSFER, OTHER
}

internal enum class MovementReason {
  HOSPITALISATION, TRANSFER, RECALL, REMAND, OTHER
}

@Service
class PrisonApiService(
  private val prisonApiWebClient: WebClient,
  @Value("\${api.prisoner-timeout:30s}") private val timeout: Duration,
) {
  internal fun getPrisonerDetails(offenderNumber: String): PrisonerDetails = prisonApiWebClient.get()
    .uri("/api/offenders/{offenderNumber}", offenderNumber)
    .retrieve()
    .bodyToMono(PrisonerDetails::class.java)
    .block(timeout)!!

  internal fun getPrisonerNumberForBookingId(bookingId: Long?): Optional<String> {
    val basicBookingDetail = prisonApiWebClient.get()
      .uri("/api/bookings/{bookingId}?basicInfo=true&extraInfo=false", bookingId)
      .retrieve()
      .bodyToMono(BasicBookingDetail::class.java)
      .block(timeout)
    return if (basicBookingDetail != null) Optional.of(basicBookingDetail.offenderNo) else Optional.empty()
  }

  internal fun getIdentifiersByBookingId(bookingId: Long?): List<BookingIdentifier>? = prisonApiWebClient.get()
    .uri("/api/bookings/{bookingId}/identifiers?type=MERGED", bookingId)
    .retrieve()
    .bodyToMono<List<BookingIdentifier>>(object : ParameterizedTypeReference<List<BookingIdentifier>>() {})
    .block(timeout)
}

internal data class PrisonerDetails(
  private val legalStatus: LegalStatus?,
  val recall: Boolean,
  val lastMovementTypeCode: String,
  val lastMovementReasonCode: String,
  val status: String?,
  val statusReason: String,
  val latestLocationId: String,
) {
  fun legalStatus(): LegalStatus = legalStatus ?: LegalStatus.UNKNOWN

  fun typeOfMovement(): MovementType = when (lastMovementTypeCode) {
    "TAP" -> MovementType.TEMPORARY_ABSENCE
    "ADM" -> MovementType.ADMISSION
    "REL" -> MovementType.RELEASED
    "CRT" -> MovementType.COURT
    "TRN" -> MovementType.TRANSFER
    else -> MovementType.OTHER
  }

  fun movementReason(): MovementReason = when (lastMovementReasonCode) {
    "HP" -> MovementReason.HOSPITALISATION
    TRANSFER_IN, TRANSFER_IN_VIA_COURT, TRANSFER_IN_VIA_TAP -> MovementReason.TRANSFER
    LICENCE_REVOKED, RECALL_FROM_HDC, RECALL_FROM_DETENTION_TRAINING_ORDER -> MovementReason.RECALL
    UNCONVICTED_REMAND -> MovementReason.REMAND
    else -> MovementReason.OTHER
  }

  fun currentLocation(): CurrentLocation? = status?.let { secondOf(it) }
    ?.let {
      when (it) {
        "IN" -> CurrentLocation.IN_PRISON
        "OUT" -> CurrentLocation.OUTSIDE_PRISON
        "TRN" -> CurrentLocation.BEING_TRANSFERRED
        else -> null
      }
    }

  fun currentPrisonStatus(): CurrentPrisonStatus? = status?.let { firstOf(it) }
    ?.let {
      when (it) {
        "ACTIVE" -> CurrentPrisonStatus.UNDER_PRISON_CARE
        "INACTIVE" -> CurrentPrisonStatus.NOT_UNDER_PRISON_CARE
        else -> null
      }
    }

  private fun firstOf(value: String): String? = elementOf(value, 0)

  private fun secondOf(value: String): String? = elementOf(value, 1)

  private fun elementOf(value: String, index: Int): String? {
    val elements = value.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    return if (elements.size > index) {
      elements[index]
    } else {
      null
    }
  }

  companion object {
    const val UNCONVICTED_REMAND = "N"
    const val LICENCE_REVOKED = "L"
    const val RECALL_FROM_DETENTION_TRAINING_ORDER = "Y"
    const val RECALL_FROM_HDC = "B"
    const val TRANSFER_IN = "INT"
    const val TRANSFER_IN_VIA_COURT = "TRNCRT"
    const val TRANSFER_IN_VIA_TAP = "TRNTAP"
  }
}

internal data class Movement(val directionCode: String, val movementDate: LocalDate)
internal data class BookingIdentifier(val value: String)
internal data class BasicBookingDetail(val offenderNo: String)
