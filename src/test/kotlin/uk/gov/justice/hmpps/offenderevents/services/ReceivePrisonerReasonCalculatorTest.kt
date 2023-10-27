package uk.gov.justice.hmpps.offenderevents.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class ReceivePrisonerReasonCalculatorTest {
  private val prisonApiService: PrisonApiService = mock()
  private val calculator: ReceivePrisonerReasonCalculator = ReceivePrisonerReasonCalculator(prisonApiService)

  @Test
  @DisplayName("TAP movement type")
  fun tAPMovementTypeTakesPrecedenceOverRecall() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("RECALL", true, "ADM"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)

    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("RECALL", true, "TAP"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.TEMPORARY_ABSENCE_RETURN)
  }

  @Test
  @DisplayName("COURT movement type")
  fun courtMovementTypeTakesPrecedenceOverRecall() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("RECALL", true, "ADM"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("RECALL", true, "CRT"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.RETURN_FROM_COURT)
  }

  @Test
  @DisplayName("movement reason of INT means reason is a TRANSFER")
  fun movementReasonOfINTMeansReasonIsATRANSFER() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("RECALL", true, "ADM", "L"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").nomisMovementReason.code)
      .isEqualTo("L")
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("RECALL", true, "ADM", "INT"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.TRANSFERRED)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").nomisMovementReason.code)
      .isEqualTo("INT")
  }

  @Test
  @DisplayName("movement reason of TRNCRT (transfer via court) means reason is a TRANSFER")
  fun movementReasonOfTRNCRTMeansReasonIsATRANSFER() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("RECALL", true, "ADM", "L"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").nomisMovementReason.code)
      .isEqualTo("L")
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("RECALL", true, "ADM", "TRNCRT"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.TRANSFERRED)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").nomisMovementReason.code)
      .isEqualTo("TRNCRT")
  }

  @Test
  @DisplayName("movement reason of TRNTAP (transfer via TAP) means reason is a TRANSFER")
  fun movementReasonOfTRNTAPMeansReasonIsATRANSFER() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("RECALL", true, "ADM", "L"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").nomisMovementReason.code)
      .isEqualTo("L")
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("RECALL", true, "ADM", "TRNTAP"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.TRANSFERRED)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").nomisMovementReason.code)
      .isEqualTo("TRNTAP")
  }

  @Test
  @DisplayName("when current status indicates not in prison then not really received")
  fun whenCurrentStatusIndicatesNotInPrisonThenNotReallyReceived() {
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
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").hasPrisonerActuallyBeenReceived())
      .isTrue()
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
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").hasPrisonerActuallyBeenReceived())
      .isFalse()
  }

  private fun prisonerDetails(
    legalStatus: String,
    recall: Boolean,
    lastMovementTypeCode: String = "ADM",
    lastMovementReasonCode: String = "K",
  ): PrisonerDetails = PrisonerDetails(
    LegalStatus.valueOf(legalStatus),
    recall,
    lastMovementTypeCode,
    lastMovementReasonCode,
    "ACTIVE IN",
    "$lastMovementTypeCode-$lastMovementReasonCode",
    "MDI",
  )
}
