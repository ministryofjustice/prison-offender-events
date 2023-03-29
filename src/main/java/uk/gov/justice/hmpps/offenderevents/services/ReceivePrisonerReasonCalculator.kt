package uk.gov.justice.hmpps.offenderevents.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.hmpps.offenderevents.services.MovementReason.RECALL
import uk.gov.justice.hmpps.offenderevents.services.MovementReason.REMAND
import uk.gov.justice.hmpps.offenderevents.services.MovementReason.TRANSFER
import java.time.LocalDate
import java.util.Objects
import java.util.Optional
import java.util.function.BiFunction
import java.util.function.Predicate

@Component
class ReceivePrisonerReasonCalculator(
  private val prisonApiService: PrisonApiService,
  private val communityApiService: CommunityApiService,
) {
  internal fun calculateMostLikelyReasonForPrisonerReceive(offenderNumber: String): ReceiveReason {
    val prisonerDetails = prisonApiService.getPrisonerDetails(offenderNumber)
    val details = "${prisonerDetails.status}:${prisonerDetails.statusReason}"
    val currentLocation = prisonerDetails.currentLocation()
    val currentPrisonStatus = prisonerDetails.currentPrisonStatus()
    val prisonId = prisonerDetails.latestLocationId
    val nomisMovementReason = MovementReason(prisonerDetails.lastMovementReasonCode)
    val withDetailsAndSource = BiFunction { reason: Reason, probableCause: ProbableCause? ->
      ReasonWithDetailsAndSource(
        reason,
        probableCause,
        Source.PRISON,
        details,
        nomisMovementReason,
      )
    }
    val reasonWithSourceAndDetails = Optional
      .ofNullable(
        when (prisonerDetails.typeOfMovement()) {
          MovementType.TEMPORARY_ABSENCE -> withDetailsAndSource.apply(
            Reason.TEMPORARY_ABSENCE_RETURN,
            null,
          )

          MovementType.COURT -> withDetailsAndSource.apply(Reason.RETURN_FROM_COURT, null)
          MovementType.ADMISSION -> when (prisonerDetails.movementReason()) {
            TRANSFER -> withDetailsAndSource.apply(
              Reason.TRANSFERRED,
              null,
            )

            RECALL -> withDetailsAndSource.apply(
              Reason.ADMISSION,
              ProbableCause.RECALL,
            )

            REMAND -> calculateReasonForPrisonerFromProbationOrEmpty(
              offenderNumber,
              details,
              nomisMovementReason,
            )
              .orElse(withDetailsAndSource.apply(Reason.ADMISSION, ProbableCause.REMAND))

            else -> null
          }

          else -> null
        },
      )
      .or {
        Optional
          .of(withDetailsAndSource.apply(Reason.ADMISSION, ProbableCause.RECALL))
          .filter { prisonerDetails.recall }
      }
      .or {
        when (prisonerDetails.legalStatus()) {
          LegalStatus.OTHER, LegalStatus.UNKNOWN, LegalStatus.CONVICTED_UNSENTENCED, LegalStatus.SENTENCED, LegalStatus.INDETERMINATE_SENTENCE -> calculateReasonForPrisonerFromProbationOrEmpty(
            offenderNumber,
            details,
            nomisMovementReason,
          )

          else -> Optional.empty()
        }
      }
      .orElseGet {
        val probableCause = when (prisonerDetails.legalStatus()) {
          LegalStatus.RECALL -> ProbableCause.RECALL
          LegalStatus.CIVIL_PRISONER, LegalStatus.CONVICTED_UNSENTENCED, LegalStatus.SENTENCED, LegalStatus.INDETERMINATE_SENTENCE -> ProbableCause.CONVICTED
          LegalStatus.IMMIGRATION_DETAINEE -> ProbableCause.IMMIGRATION_DETAINEE
          LegalStatus.REMAND -> ProbableCause.REMAND
          LegalStatus.DEAD, LegalStatus.OTHER, LegalStatus.UNKNOWN -> ProbableCause.UNKNOWN
        }
        ReasonWithDetailsAndSource(
          Reason.ADMISSION,
          probableCause,
          Source.PRISON,
          details,
          MovementReason(prisonerDetails.lastMovementReasonCode),
        )
      }
    return ReceiveReason(
      reasonWithSourceAndDetails.reason,
      reasonWithSourceAndDetails.probableCause,
      reasonWithSourceAndDetails.source,
      reasonWithSourceAndDetails.details,
      currentLocation,
      currentPrisonStatus,
      prisonId,
      nomisMovementReason,
    )
  }

  private fun calculateReasonForPrisonerFromProbationOrEmpty(
    offenderNumber: String,
    details: String,
    nomisMovementReason: MovementReason,
  ): Optional<ReasonWithDetailsAndSource> {
    // be lenient with current movement in case the event has been delayed for few days due to a system issue
    val excludingCurrentMovement = Predicate { movement: Movement ->
      movement
        .movementDate
        .isBefore(LocalDate.now().minusDays(4))
    }
    val hasBeenInPrisonSince = Predicate { referralDate: LocalDate ->
      val movements = prisonApiService.getMovements(offenderNumber)
      val lastPrisonEntryDate = movements
        .stream()
        .filter { movement: Movement -> "IN" == movement.directionCode }
        .filter(excludingCurrentMovement)
        .filter { movement: Movement ->
          movement.movementDate.isAfter(referralDate) || (movement.movementDate == referralDate)
        }
        .findAny()
      lastPrisonEntryDate.ifPresent { date: Movement? ->
        log.debug("Last time in prison was {}", date)
      }
      lastPrisonEntryDate.isPresent
    }
    val recalls = communityApiService.getRecalls(offenderNumber)
    return recalls
      .stream()
      .filter { recall: Recall -> hasActiveOrCompletedRecall(recall) }
      .map(Recall::referralDate)
      .filter { obj: LocalDate? -> Objects.nonNull(obj) }
      .max { obj: LocalDate, other: LocalDate? -> obj.compareTo(other) }
      .filter(Predicate.not(hasBeenInPrisonSince))
      .map { referralDate: LocalDate? ->
        ReasonWithDetailsAndSource(
          Reason.ADMISSION,
          ProbableCause.RECALL,
          Source.PROBATION,
          "$details Recall referral date $referralDate",
          nomisMovementReason,
        )
      }
  }

  private fun hasActiveOrCompletedRecall(recall: Recall): Boolean {
    if (recall.outcomeRecall != null) {
      return recall.outcomeRecall
    }
    return if (recall.recallRejectedOrWithdrawn != null) {
      !recall.recallRejectedOrWithdrawn
    } else {
      false
    }
  }

  enum class Reason {
    ADMISSION, TEMPORARY_ABSENCE_RETURN, RETURN_FROM_COURT, TRANSFERRED
  }

  enum class ProbableCause {
    RECALL, REMAND, CONVICTED, IMMIGRATION_DETAINEE, UNKNOWN
  }

  enum class Source {
    PRISON, PROBATION
  }

  internal data class MovementReason(val code: String)

  internal data class ReasonWithDetailsAndSource(
    val reason: Reason,
    val probableCause: ProbableCause?,
    val source: Source,
    val details: String?,
    val nomisMovementReason: MovementReason,
  )

  internal data class ReceiveReason(
    val reason: Reason,
    val probableCause: ProbableCause?,
    val source: Source,
    override val details: String? = null,
    override val currentLocation: CurrentLocation,
    override val currentPrisonStatus: CurrentPrisonStatus,
    override val prisonId: String,
    val nomisMovementReason: MovementReason,
  ) : PrisonerMovementReason {
    fun hasPrisonerActuallyBeenReceived(): Boolean = currentLocation == CurrentLocation.IN_PRISON
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
