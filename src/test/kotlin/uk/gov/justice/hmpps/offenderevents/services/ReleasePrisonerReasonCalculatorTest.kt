package uk.gov.justice.hmpps.offenderevents.services
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class ReleasePrisonerReasonCalculatorTest {
  private val prisonApiService: PrisonApiService = mock()
  private val calculator: ReleasePrisonerReasonCalculator = ReleasePrisonerReasonCalculator(prisonApiService)

  @Test
  @DisplayName("when last movement is TAP reason is temporary absence")
  fun whenLastMovementIsTAPReasonIsTemporaryAbsence() {
    whenever(prisonApiService.getPrisonerDetails("A1234GF")).thenReturn(prisonerDetails("TAP"))
    val reason = calculator.calculateReasonForRelease("A1234GF")
    assertThat(reason.reason).isEqualTo(ReleasePrisonerReasonCalculator.Reason.TEMPORARY_ABSENCE_RELEASE)
  }

  @Test
  @DisplayName("when last movement is CRT then reason is sent to court")
  fun whenLastMovementIsCRTThenReasonIsSentToCourt() {
    whenever(prisonApiService.getPrisonerDetails("A1234GF")).thenReturn(prisonerDetails("CRT"))
    val reason = calculator.calculateReasonForRelease("A1234GF")
    assertThat(reason.reason).isEqualTo(ReleasePrisonerReasonCalculator.Reason.SENT_TO_COURT)
  }

  @Test
  @DisplayName("when last movement is a TRN then the reason is transferred")
  fun whenLastMovementIsATRNThenTheReasonIsTransfer() {
    whenever(prisonApiService.getPrisonerDetails("A1234GF")).thenReturn(prisonerDetails("TRN"))
    val reason = calculator.calculateReasonForRelease("A1234GF")
    assertThat(reason.reason).isEqualTo(ReleasePrisonerReasonCalculator.Reason.TRANSFERRED)
  }

  @Test
  @DisplayName("when release to hospital reason is hospital release")
  fun whenReleaseToHospitalReasonIsHospitalRelease() {
    whenever(prisonApiService.getPrisonerDetails("A1234GF")).thenReturn(prisonerDetails("REL", "HP"))
    val reason = calculator.calculateReasonForRelease("A1234GF")
    assertThat(reason.reason).isEqualTo(ReleasePrisonerReasonCalculator.Reason.RELEASED_TO_HOSPITAL)
    assertThat(reason.nomisMovementReason.code).isEqualTo("HP")
  }

  @Test
  @DisplayName("will add movement type to reason when we do not know the type")
  fun willAddMovementTypeToReasonWhenWeDoNotKnowTheType() {
    whenever(prisonApiService.getPrisonerDetails("A1234GF")).thenReturn(prisonerDetails("XXX"))
    val reason = calculator.calculateReasonForRelease("A1234GF")
    assertThat(reason.reason).isEqualTo(ReleasePrisonerReasonCalculator.Reason.UNKNOWN)
    assertThat(reason.details).isEqualTo("Movement type code XXX")
  }

  @Test
  @DisplayName("will add movement reason codes when we do not know reason")
  fun willAddMovementReasonCodesWhenWeDoNotKnowReason() {
    whenever(prisonApiService.getPrisonerDetails("A1234GF")).thenReturn(prisonerDetails("REL", "YY"))
    val reason = calculator.calculateReasonForRelease("A1234GF")
    assertThat(reason.reason).isEqualTo(ReleasePrisonerReasonCalculator.Reason.RELEASED)
    assertThat(reason.details).isEqualTo("Movement reason code YY")
    assertThat(reason.nomisMovementReason.code).isEqualTo("YY")
  }

  @Test
  @DisplayName("when status indicates still in prison then not really released")
  fun whenStatusIndicatesStillInPrisonThenNotReallyReleased() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(
        PrisonerDetails(
          LegalStatus.REMAND,
          false,
          "TRN",
          "P",
          "INACTIVE OUT",
          "TRN-P",
          "MDI",
        ),
      )
    assertThat(calculator.calculateReasonForRelease("A1234GH").hasPrisonerActuallyBeenRelease()).isTrue()
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(
        PrisonerDetails(
          LegalStatus.REMAND,
          false,
          "REL",
          "P",
          "INACTIVE OUT",
          "TRN-P",
          "MDI",
        ),
      )
    assertThat(calculator.calculateReasonForRelease("A1234GH").hasPrisonerActuallyBeenRelease()).isTrue()
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(
        PrisonerDetails(
          LegalStatus.REMAND,
          false,
          "ADM",
          "L",
          "ACTIVE IN",
          "ADM-L",
          "MDI",
        ),
      )
    assertThat(calculator.calculateReasonForRelease("A1234GH").hasPrisonerActuallyBeenRelease()).isFalse()
  }

  private fun prisonerDetails(lastMovementTypCode: String, lastMovementReasonCode: String = "N"): PrisonerDetails = PrisonerDetails(
    LegalStatus.SENTENCED,
    false,
    lastMovementTypCode,
    lastMovementReasonCode,
    "ACTIVE OUT",
    "$lastMovementTypCode-$lastMovementReasonCode",
    "MDI",
  )
}
