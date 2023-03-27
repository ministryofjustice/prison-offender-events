package uk.gov.justice.hmpps.offenderevents.services

import org.springframework.stereotype.Component
import uk.gov.justice.hmpps.offenderevents.services.MovementReason.HOSPITALISATION

@Component
class ReleasePrisonerReasonCalculator(private val prisonApiService: PrisonApiService) {
  internal fun calculateReasonForRelease(offenderNumber: String): ReleaseReason {
    val prisonerDetails = prisonApiService.getPrisonerDetails(offenderNumber)
    val currentLocation = prisonerDetails.currentLocation()
    val currentPrisonStatus = prisonerDetails.currentPrisonStatus()
    val latestLocationId = prisonerDetails.latestLocationId
    val nomisMovementReason = MovementReason(prisonerDetails.lastMovementReasonCode)
    val reasonWithDetails = when (prisonerDetails.typeOfMovement()) {
      MovementType.TEMPORARY_ABSENCE -> ReasonWithDetails(
        reason = Reason.TEMPORARY_ABSENCE_RELEASE,
        nomisMovementReason = MovementReason(prisonerDetails.lastMovementReasonCode),
      )

      MovementType.COURT -> ReasonWithDetails(
        reason = Reason.SENT_TO_COURT,
        nomisMovementReason = MovementReason(prisonerDetails.lastMovementReasonCode),
      )

      MovementType.TRANSFER -> ReasonWithDetails(
        Reason.TRANSFERRED,
        nomisMovementReason = MovementReason(prisonerDetails.lastMovementReasonCode),
      )

      MovementType.RELEASED -> {
        val reason = when (prisonerDetails.movementReason()) {
          HOSPITALISATION -> Reason.RELEASED_TO_HOSPITAL
          else -> Reason.RELEASED
        }
        ReasonWithDetails(
          reason,
          "Movement reason code ${prisonerDetails.lastMovementReasonCode}",
          MovementReason(prisonerDetails.lastMovementReasonCode),
        )
      }

      else -> ReasonWithDetails(
        Reason.UNKNOWN,
        "Movement type code ${prisonerDetails.lastMovementTypeCode}",
        MovementReason(prisonerDetails.lastMovementReasonCode),
      )
    }
    return ReleaseReason(
      reasonWithDetails.reason,
      reasonWithDetails.details,
      currentLocation,
      currentPrisonStatus,
      latestLocationId,
      nomisMovementReason,
    )
  }

  internal enum class Reason {
    UNKNOWN, TEMPORARY_ABSENCE_RELEASE, RELEASED_TO_HOSPITAL, RELEASED, SENT_TO_COURT, TRANSFERRED
  }

  internal data class MovementReason(val code: String)

  internal data class ReasonWithDetails(
    val reason: Reason,
    val details: String? = null,
    val nomisMovementReason: MovementReason,
  )

  internal data class ReleaseReason(
    val reason: Reason,
    override val details: String? = null,
    override val currentLocation: CurrentLocation,
    override val currentPrisonStatus: CurrentPrisonStatus,
    override val prisonId: String,
    val nomisMovementReason: MovementReason,
  ) : PrisonerMovementReason {
    fun hasPrisonerActuallyBeenRelease(): Boolean = currentLocation != CurrentLocation.IN_PRISON
  }
}
