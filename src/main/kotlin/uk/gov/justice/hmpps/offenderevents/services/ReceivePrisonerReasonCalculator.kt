package uk.gov.justice.hmpps.offenderevents.services

import org.springframework.stereotype.Component
import uk.gov.justice.hmpps.offenderevents.services.MovementReason.TRANSFER

@Component
class ReceivePrisonerReasonCalculator(
  private val prisonApiService: PrisonApiService,
) {
  internal fun calculateMostLikelyReasonForPrisonerReceive(offenderNumber: String): ReceiveReason {
    val prisonerDetails = prisonApiService.getPrisonerDetails(offenderNumber)

    val reason = when (prisonerDetails.typeOfMovement()) {
      MovementType.TEMPORARY_ABSENCE -> Reason.TEMPORARY_ABSENCE_RETURN
      MovementType.COURT -> Reason.RETURN_FROM_COURT
      MovementType.ADMISSION -> when (prisonerDetails.movementReason()) {
        TRANSFER -> Reason.TRANSFERRED
        else -> Reason.ADMISSION
      }
      else -> Reason.ADMISSION
    }

    return ReceiveReason(
      reason = reason,
      details = "${prisonerDetails.status}:${prisonerDetails.statusReason}",
      currentLocation = prisonerDetails.currentLocation(),
      currentPrisonStatus = prisonerDetails.currentPrisonStatus(),
      prisonId = prisonerDetails.latestLocationId,
      nomisMovementReason = MovementReason(prisonerDetails.lastMovementReasonCode),
    )
  }

  enum class Reason {
    ADMISSION,
    TEMPORARY_ABSENCE_RETURN,
    RETURN_FROM_COURT,
    TRANSFERRED,
  }

  internal data class MovementReason(val code: String)

  internal data class ReceiveReason(
    val reason: Reason,
    override val details: String? = null,
    override val currentLocation: CurrentLocation?,
    override val currentPrisonStatus: CurrentPrisonStatus?,
    override val prisonId: String,
    val nomisMovementReason: MovementReason,
  ) : PrisonerMovementReason {
    fun hasPrisonerActuallyBeenReceived(): Boolean = currentLocation == CurrentLocation.IN_PRISON
  }
}
