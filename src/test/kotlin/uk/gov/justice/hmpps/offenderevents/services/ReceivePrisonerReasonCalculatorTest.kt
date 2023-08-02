package uk.gov.justice.hmpps.offenderevents.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClientResponseException
import scala.Array.emptyByteArray
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.ProbableCause
import java.time.LocalDate
import java.util.stream.Stream

@TestInstance(PER_CLASS)
internal class ReceivePrisonerReasonCalculatorTest {
  private val prisonApiService: PrisonApiService = mock()
  private val communityApiService: CommunityApiService = mock()
  private val calculator: ReceivePrisonerReasonCalculator = ReceivePrisonerReasonCalculator(prisonApiService, communityApiService)

  @BeforeEach
  fun resetMocks() {
    reset(prisonApiService, communityApiService)
  }

  @Test
  @DisplayName("probable cause is recall of both legal status RECALL and calculated recall true")
  fun reasonIsRecallIfBothLegalStatusRECALLAndCalculatedRecallTrue() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("RECALL", true))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.RECALL)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
  }

  @Test
  @DisplayName("reason is recall if only calculated recall is true")
  fun reasonIsRecallIfOnlyCalculatedRecallIsTrue() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("UNKNOWN", true))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.RECALL)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
  }

  @Test
  @DisplayName("TAP movement type takes precedence over recall")
  fun tAPMovementTypeTakesPrecedenceOverRecall() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("RECALL", true, "ADM"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.RECALL)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("RECALL", true, "TAP"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.TEMPORARY_ABSENCE_RETURN)
  }

  @Test
  @DisplayName("COURT movement type takes precedence over recall")
  fun courtMovementTypeTakesPrecedenceOverRecall() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("RECALL", true, "ADM"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.RECALL)
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
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.RECALL)
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
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.RECALL)
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
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.RECALL)
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
  @DisplayName("movement reason of L (Licence Revokee) means probable cause is a RECALL")
  fun movementReasonOfLMeansReasonIsARECALL() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("SENTENCED", false, "ADM", "V"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.CONVICTED)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").nomisMovementReason.code)
      .isEqualTo("V")
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("SENTENCED", false, "ADM", "L"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.RECALL)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").nomisMovementReason.code)
      .isEqualTo("L")
  }

  @Test
  @DisplayName("movement reason of Y (Recall from Detention Training Order) means probable cause is a RECALL")
  fun movementReasonOfYMeansReasonIsARECALL() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("SENTENCED", false, "ADM", "V"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.CONVICTED)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").nomisMovementReason.code)
      .isEqualTo("V")
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("SENTENCED", false, "ADM", "Y"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.RECALL)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").nomisMovementReason.code)
      .isEqualTo("Y")
  }

  @Test
  @DisplayName("movement reason of B (HDC Recall) means probable cause is a RECALL")
  fun movementReasonOfBMeansReasonIsARECALL() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("SENTENCED", false, "ADM", "V"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.CONVICTED)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").nomisMovementReason.code)
      .isEqualTo("V")
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("SENTENCED", false, "ADM", "B"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.RECALL)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").nomisMovementReason.code)
      .isEqualTo("B")
  }

  @Test
  @DisplayName("movement reason of N (Unconvicted Remand) means probable cause is a REMAND when no recall NSI")
  fun movementReasonOfNMeansReasonIsREMAND() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("SENTENCED", false, "ADM", "V"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.CONVICTED)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").nomisMovementReason.code)
      .isEqualTo("V")
    whenever(communityApiService.getRecalls(any())).thenReturn(emptyList())
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("SENTENCED", false, "ADM", "N"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.REMAND)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").nomisMovementReason.code)
      .isEqualTo("N")
  }

  @Test
  @DisplayName("movement reason of N (Unconvicted Remand) means probable cause is a RECALL when there is a recall NSI")
  fun movementReasonOfNMeansReasonIsRECALLWithRecallNSI() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("SENTENCED", false, "ADM", "V"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.CONVICTED)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    whenever(communityApiService.getRecalls(any()))
      .thenReturn(listOf(Recall(LocalDate.now(), false, null)))
    whenever(prisonApiService.getMovements(any())).thenReturn(
      listOf(
        Movement("IN", LocalDate.now()),
        Movement("OUT", LocalDate.parse("2021-06-13")),
        Movement("IN", LocalDate.parse("2021-05-13")),
      ),
    )
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("SENTENCED", false, "ADM", "N"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.RECALL)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
  }

  @Test
  @DisplayName("probable cause is UNKNOWN when legal status is UNKNOWN and recall is false")
  fun reasonInUNKNOWNWhenLegalStatusIsUNKNOWNAndRecallIsFalse() {
    whenever(communityApiService.getRecalls(any())).thenReturn(emptyList())
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("UNKNOWN", false))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.UNKNOWN)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
  }

  @Test
  @DisplayName("probable cause is UNKNOWN when communityApiService call fails")
  fun reasonInUNKNOWNWhenDeliusError() {
    whenever(communityApiService.getRecalls(any())).thenThrow(
      WebClientResponseException
        .create(500, "test system error", HttpHeaders(), emptyByteArray(), null),
    )
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("UNKNOWN", false))
    val result = calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH")
    assertThat(result.probableCause).isEqualTo(ProbableCause.UNKNOWN)
    assertThat(result.reason).isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    assertThat(result.details).isEqualTo("ACTIVE IN:ADM-K Error getting Delius recall details: 500 test system error")
  }

  @Test
  @DisplayName("probable cause is REMAND when legal status is REMAND")
  fun reasonIsREMANDWhenLegalStatusIsREMAND() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails("REMAND", false))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.REMAND)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
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

  @ParameterizedTest
  @MethodSource("legalStatusMap")
  @DisplayName("legal status is mapped to reason")
  @MockitoSettings(strictness = Strictness.LENIENT)
  fun legalStatusIsMappedToReason(legalStatus: LegalStatus, probableCause: ProbableCause?) {
    whenever(communityApiService.getRecalls(any())).thenReturn(emptyList())
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(prisonerDetails(legalStatus.name, false))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(probableCause)
  }

  @Test
  @DisplayName("missing legal status is treated as unknown")
  fun missingLegalStatusIsTreatedAsUnknown() {
    whenever(communityApiService.getRecalls(any())).thenReturn(emptyList())
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(PrisonerDetails(null, false, "XXX", "XXX", "ACTIVE IN", "XXX-XXX", "MDI"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.UNKNOWN)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
  }

  @Test
  @DisplayName("can still calculate remand when legal status is empty")
  fun canStillCalculateRemandWhenLegalStatusIsEmpty() {
    whenever(prisonApiService.getPrisonerDetails(any()))
      .thenReturn(PrisonerDetails(null, false, "ADM", "N", "ACTIVE IN", "ADM-N", "MDI"))
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
      .isEqualTo(ProbableCause.REMAND)
    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
      .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
  }

  private fun prisonerDetails(
    legalStatus: String,
    recall: Boolean,
    lastMovementTypeCode: String = "ADM",
    lastMovementReasonCode: String = "K",
  ): PrisonerDetails {
    return PrisonerDetails(
      LegalStatus.valueOf(legalStatus),
      recall,
      lastMovementTypeCode,
      lastMovementReasonCode,
      "ACTIVE IN",
      String.format("%s-%s", lastMovementTypeCode, lastMovementReasonCode),
      "MDI",
    )
  }

  @Nested
  @DisplayName("when delegating to probation")
  internal inner class WhenDelegatingToProbation {
    @Nested
    @DisplayName("when offender not found")
    @TestInstance(PER_CLASS)
    internal inner class WhenOffenderNotFound {
      @BeforeEach
      fun setUp() {
        whenever(communityApiService.getRecalls(any())).thenReturn(emptyList())
      }

      @ParameterizedTest
      @MethodSource("legalStatusMap")
      @DisplayName("legal status is mapped to probable cause")
      @MockitoSettings(strictness = Strictness.LENIENT)
      fun legalStatusIsMappedToReason(legalStatus: LegalStatus, probableCause: ProbableCause?) {
        whenever(prisonApiService.getPrisonerDetails(any()))
          .thenReturn(prisonerDetails(legalStatus.name, false))
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
          .isEqualTo(probableCause)
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
          .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
      }

      private fun legalStatusMap(): Stream<Arguments> {
        return Stream.of(
          Arguments.of(LegalStatus.INDETERMINATE_SENTENCE, ProbableCause.CONVICTED),
          Arguments.of(LegalStatus.SENTENCED, ProbableCause.CONVICTED),
          Arguments.of(LegalStatus.CIVIL_PRISONER, ProbableCause.CONVICTED),
          Arguments.of(LegalStatus.CONVICTED_UNSENTENCED, ProbableCause.CONVICTED),
          Arguments.of(LegalStatus.DEAD, ProbableCause.UNKNOWN),
          Arguments.of(LegalStatus.OTHER, ProbableCause.UNKNOWN),
          Arguments.of(LegalStatus.UNKNOWN, ProbableCause.UNKNOWN),
          Arguments.of(LegalStatus.IMMIGRATION_DETAINEE, ProbableCause.IMMIGRATION_DETAINEE),
        )
      }
    }
  }

  @Nested
  @TestInstance(PER_CLASS)
  @DisplayName("when offender has no recall requests")
  internal inner class WhenOffenderHasNoRecallRequest {
    @BeforeEach
    fun setUp() {
      whenever(communityApiService.getRecalls(any())).thenReturn(emptyList())
    }

    @ParameterizedTest
    @MethodSource("legalStatusMap")
    @DisplayName("legal status is mapped to probable cause")
    @MockitoSettings(strictness = Strictness.LENIENT)
    fun legalStatusIsMappedToReason(legalStatus: LegalStatus, probableCause: ProbableCause?) {
      whenever(prisonApiService.getPrisonerDetails(any()))
        .thenReturn(prisonerDetails(legalStatus.name, false))
      assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
        .isEqualTo(probableCause)
      assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
        .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    }

    private fun legalStatusMap(): Stream<Arguments> {
      return Stream.of(
        Arguments.of(LegalStatus.INDETERMINATE_SENTENCE, ProbableCause.CONVICTED),
        Arguments.of(LegalStatus.SENTENCED, ProbableCause.CONVICTED),
        Arguments.of(LegalStatus.CIVIL_PRISONER, ProbableCause.CONVICTED),
        Arguments.of(LegalStatus.CONVICTED_UNSENTENCED, ProbableCause.CONVICTED),
        Arguments.of(LegalStatus.DEAD, ProbableCause.UNKNOWN),
        Arguments.of(LegalStatus.OTHER, ProbableCause.UNKNOWN),
        Arguments.of(LegalStatus.UNKNOWN, ProbableCause.UNKNOWN),
        Arguments.of(LegalStatus.IMMIGRATION_DETAINEE, ProbableCause.IMMIGRATION_DETAINEE),
      )
    }
  }

  @Nested
  @TestInstance(PER_CLASS)
  @DisplayName("when has recall in progress with no outcome")
  internal inner class WhenHasRecallInProgressWithNoOutcome {
    @BeforeEach
    fun setUp() {
      whenever(communityApiService.getRecalls(any()))
        .thenReturn(listOf(Recall(LocalDate.now(), false, null)))
      whenever(prisonApiService.getMovements(any()))
        .thenReturn(listOf(Movement("OUT", LocalDate.now().minusYears(99))))
    }

    @ParameterizedTest
    @MethodSource("legalStatusMap")
    @DisplayName("legal status is mapped to probable cause")
    @MockitoSettings(strictness = Strictness.LENIENT)
    fun legalStatusIsMappedToReason(legalStatus: LegalStatus, probableCause: ProbableCause?) {
      whenever(prisonApiService.getPrisonerDetails(any()))
        .thenReturn(prisonerDetails(legalStatus.name, false))
      assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
        .isEqualTo(probableCause)
      assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
        .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    }

    private fun legalStatusMap(): Stream<Arguments> {
      return Stream.of(
        Arguments.of(LegalStatus.INDETERMINATE_SENTENCE, ProbableCause.RECALL),
        Arguments.of(LegalStatus.SENTENCED, ProbableCause.RECALL),
        Arguments.of(LegalStatus.CIVIL_PRISONER, ProbableCause.CONVICTED),
        Arguments.of(LegalStatus.CONVICTED_UNSENTENCED, ProbableCause.RECALL),
        Arguments.of(LegalStatus.DEAD, ProbableCause.UNKNOWN),
        Arguments.of(LegalStatus.OTHER, ProbableCause.RECALL),
        Arguments.of(LegalStatus.UNKNOWN, ProbableCause.RECALL),
        Arguments.of(LegalStatus.IMMIGRATION_DETAINEE, ProbableCause.IMMIGRATION_DETAINEE),
      )
    }
  }

  @Nested
  @TestInstance(PER_CLASS)
  @DisplayName("when has recall rejected")
  internal inner class WhenHasRecallRejected {
    @BeforeEach
    fun setUp() {
      whenever(communityApiService.getRecalls(any()))
        .thenReturn(listOf(Recall(LocalDate.now(), true, null)))
    }

    @ParameterizedTest
    @MethodSource("legalStatusMap")
    @DisplayName("legal status is mapped to probable cause")
    @MockitoSettings(strictness = Strictness.LENIENT)
    fun legalStatusIsMappedToReason(legalStatus: LegalStatus, probableCause: ProbableCause?) {
      whenever(prisonApiService.getPrisonerDetails(any()))
        .thenReturn(prisonerDetails(legalStatus.name, false))
      assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
        .isEqualTo(probableCause)
      assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
        .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    }

    @Test
    @DisplayName("reason source will be prison")
    fun reasonSourceWillBeProbationWhenOverridden() {
      whenever(prisonApiService.getPrisonerDetails(any()))
        .thenReturn(prisonerDetails(LegalStatus.SENTENCED.name, false))
      assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").source)
        .isEqualTo(ReceivePrisonerReasonCalculator.Source.PRISON)
      whenever(prisonApiService.getPrisonerDetails(any()))
        .thenReturn(prisonerDetails(LegalStatus.IMMIGRATION_DETAINEE.name, false))
      assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").source)
        .isEqualTo(ReceivePrisonerReasonCalculator.Source.PRISON)
    }

    private fun legalStatusMap(): Stream<Arguments> {
      return Stream.of(
        Arguments.of(LegalStatus.INDETERMINATE_SENTENCE, ProbableCause.CONVICTED),
        Arguments.of(LegalStatus.SENTENCED, ProbableCause.CONVICTED),
        Arguments.of(LegalStatus.CIVIL_PRISONER, ProbableCause.CONVICTED),
        Arguments.of(LegalStatus.CONVICTED_UNSENTENCED, ProbableCause.CONVICTED),
        Arguments.of(LegalStatus.DEAD, ProbableCause.UNKNOWN),
        Arguments.of(LegalStatus.OTHER, ProbableCause.UNKNOWN),
        Arguments.of(LegalStatus.UNKNOWN, ProbableCause.UNKNOWN),
        Arguments.of(LegalStatus.IMMIGRATION_DETAINEE, ProbableCause.IMMIGRATION_DETAINEE),
      )
    }
  }

  @Nested
  @TestInstance(PER_CLASS)
  @DisplayName("when has recall outcome but not a recall")
  internal inner class WhenHasRecallOutcomeButNotRecall {
    @BeforeEach
    fun setUp() {
      // currently this will not happen, if the outcome is not a recall then it must have been rejected in which case
      // recall would be in rejected state anyway - but tests incase community-api behavior changes
      whenever(communityApiService.getRecalls(any()))
        .thenReturn(listOf(Recall(LocalDate.now(), false, false)))
    }

    @ParameterizedTest
    @MethodSource("legalStatusMap")
    @DisplayName("legal status is mapped to probable cause")
    @MockitoSettings(strictness = Strictness.LENIENT)
    fun legalStatusIsMappedToReason(legalStatus: LegalStatus, probableCause: ProbableCause?) {
      whenever(prisonApiService.getPrisonerDetails(any()))
        .thenReturn(prisonerDetails(legalStatus.name, false))
      assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
        .isEqualTo(probableCause)
      assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
        .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    }

    private fun legalStatusMap(): Stream<Arguments> {
      return Stream.of(
        Arguments.of(LegalStatus.INDETERMINATE_SENTENCE, ProbableCause.CONVICTED),
        Arguments.of(LegalStatus.SENTENCED, ProbableCause.CONVICTED),
        Arguments.of(LegalStatus.CIVIL_PRISONER, ProbableCause.CONVICTED),
        Arguments.of(LegalStatus.CONVICTED_UNSENTENCED, ProbableCause.CONVICTED),
        Arguments.of(LegalStatus.DEAD, ProbableCause.UNKNOWN),
        Arguments.of(LegalStatus.OTHER, ProbableCause.UNKNOWN),
        Arguments.of(LegalStatus.UNKNOWN, ProbableCause.UNKNOWN),
        Arguments.of(LegalStatus.IMMIGRATION_DETAINEE, ProbableCause.IMMIGRATION_DETAINEE),
      )
    }
  }

  @Nested
  @TestInstance(PER_CLASS)
  @DisplayName("when has recall outcome that is a recall")
  internal inner class WhenHasRecallOutcomeIsRecall {
    @BeforeEach
    fun setUp() {
      whenever(communityApiService.getRecalls(any()))
        .thenReturn(listOf(Recall(LocalDate.parse("2021-06-13"), false, true)))
      whenever(prisonApiService.getMovements(any()))
        .thenReturn(listOf(Movement("OUT", LocalDate.now().minusYears(99))))
    }

    @ParameterizedTest
    @MethodSource("legalStatusMap")
    @DisplayName("legal status is mapped to probable cause")
    @MockitoSettings(strictness = Strictness.LENIENT)
    fun legalStatusIsMappedToReason(legalStatus: LegalStatus, probableCause: ProbableCause?) {
      whenever(prisonApiService.getPrisonerDetails(any()))
        .thenReturn(prisonerDetails(legalStatus.name, false))
      assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
        .isEqualTo(probableCause)
      assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
        .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
    }

    @Test
    @DisplayName("reason source will be probation when overridden")
    fun reasonSourceWillBeProbationWhenOverridden() {
      whenever(prisonApiService.getPrisonerDetails(any()))
        .thenReturn(prisonerDetails(LegalStatus.SENTENCED.name, false))
      assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").source)
        .isEqualTo(ReceivePrisonerReasonCalculator.Source.PROBATION)
      whenever(prisonApiService.getPrisonerDetails(any()))
        .thenReturn(prisonerDetails(LegalStatus.IMMIGRATION_DETAINEE.name, false))
      assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").source)
        .isEqualTo(ReceivePrisonerReasonCalculator.Source.PRISON)
    }

    @Test
    @DisplayName("will add a further information message when overridden")
    fun willAddAFurtherInformationMessageWhenOverridden() {
      whenever(prisonApiService.getPrisonerDetails(any()))
        .thenReturn(prisonerDetails(LegalStatus.SENTENCED.name, false))
      assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").details)
        .isEqualTo("ACTIVE IN:ADM-K Recall referral date 2021-06-13")
      whenever(prisonApiService.getPrisonerDetails(any()))
        .thenReturn(prisonerDetails(LegalStatus.IMMIGRATION_DETAINEE.name, false))
      assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").details)
        .isEqualTo("ACTIVE IN:ADM-K")
    }

    private fun legalStatusMap(): Stream<Arguments> {
      return Stream.of(
        Arguments.of(LegalStatus.INDETERMINATE_SENTENCE, ProbableCause.RECALL),
        Arguments.of(LegalStatus.SENTENCED, ProbableCause.RECALL),
        Arguments.of(LegalStatus.CIVIL_PRISONER, ProbableCause.CONVICTED),
        Arguments.of(LegalStatus.CONVICTED_UNSENTENCED, ProbableCause.RECALL),
        Arguments.of(LegalStatus.DEAD, ProbableCause.UNKNOWN),
        Arguments.of(LegalStatus.OTHER, ProbableCause.RECALL),
        Arguments.of(LegalStatus.UNKNOWN, ProbableCause.RECALL),
        Arguments.of(LegalStatus.IMMIGRATION_DETAINEE, ProbableCause.IMMIGRATION_DETAINEE),
      )
    }
  }

  @Nested
  @DisplayName("with multiple recall outcomes")
  internal inner class WithAMultipleOutcomes {
    @BeforeEach
    fun setUp() {
      whenever(prisonApiService.getPrisonerDetails(any()))
        .thenReturn(prisonerDetails(LegalStatus.SENTENCED.name, false))
      whenever(communityApiService.getRecalls(any()))
        .thenReturn(
          listOf(
            Recall(LocalDate.parse("2021-06-13"), false, true),
            Recall(LocalDate.parse("2020-06-13"), false, true),
          ),
        )
    }

    @Nested
    @DisplayName("with just today's movement")
    internal inner class NoPreviousMovements {
      @BeforeEach
      fun setUp() {
        whenever(prisonApiService.getMovements(any()))
          .thenReturn(listOf(Movement("IN", LocalDate.now())))
      }

      @Test
      @DisplayName("recall will be treated as current")
      fun recallWillBeTreatedAsCurrent() {
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
          .isEqualTo(ProbableCause.RECALL)
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
          .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
      }
    }

    @Nested
    @DisplayName("with just a very recent movement")
    internal inner class NoOldishPreviousMovements {
      @BeforeEach
      fun setUp() {
        whenever(prisonApiService.getMovements(any()))
          .thenReturn(listOf(Movement("IN", LocalDate.now().minusDays(2))))
      }

      @Test
      @DisplayName("recall will be treated as current")
      fun recallWillBeTreatedAsCurrent() {
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
          .isEqualTo(ProbableCause.RECALL)
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
          .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
      }
    }

    @Nested
    @DisplayName("with today's movement and really old one")
    internal inner class NoRecentPreviousMovements {
      @BeforeEach
      fun setUp() {
        whenever(prisonApiService.getMovements(any())).thenReturn(
          listOf(
            Movement("IN", LocalDate.now()),
            Movement("OUT", LocalDate.parse("2010-06-13")),
            Movement("IN", LocalDate.parse("2010-05-13")),
          ),
        )
      }

      @Test
      @DisplayName("recall will be treated as current")
      fun recallWillBeTreatedAsCurrent() {
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
          .isEqualTo(ProbableCause.RECALL)
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
          .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
      }
    }

    @Nested
    @DisplayName("with today's movement and one after first recall but before latest recall")
    internal inner class PreviousBetweenRecallsMovements {
      @BeforeEach
      fun setUp() {
        whenever(prisonApiService.getMovements(any())).thenReturn(
          listOf(
            Movement("IN", LocalDate.now()),
            Movement("OUT", LocalDate.parse("2021-06-13")),
            Movement("IN", LocalDate.parse("2021-05-13")),
          ),
        )
      }

      @Test
      @DisplayName("recall will be treated as current")
      fun recallWillBeTreatedAsCurrent() {
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
          .isEqualTo(ProbableCause.RECALL)
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
          .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
      }
    }

    @Nested
    @DisplayName("with today's movement and one after latest recall")
    internal inner class PreviousAfterRecallsMovements {
      @BeforeEach
      fun setUp() {
        whenever(prisonApiService.getMovements(any())).thenReturn(
          listOf(
            Movement("IN", LocalDate.now()),
            Movement("IN", LocalDate.parse("2021-06-13")),
            Movement("OUT", LocalDate.parse("2021-06-23")),
          ),
        )
      }

      @Test
      @DisplayName("recall will be ignored")
      fun recallWillBeTreatedAsCurrent() {
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").probableCause)
          .isEqualTo(ProbableCause.CONVICTED)
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason)
          .isEqualTo(ReceivePrisonerReasonCalculator.Reason.ADMISSION)
      }
    }
  }

  private fun legalStatusMap(): Stream<Arguments> {
    return Stream.of(
      Arguments.of(LegalStatus.INDETERMINATE_SENTENCE, ProbableCause.CONVICTED),
      Arguments.of(LegalStatus.SENTENCED, ProbableCause.CONVICTED),
      Arguments.of(LegalStatus.CIVIL_PRISONER, ProbableCause.CONVICTED),
      Arguments.of(LegalStatus.CONVICTED_UNSENTENCED, ProbableCause.CONVICTED),
      Arguments.of(LegalStatus.DEAD, ProbableCause.UNKNOWN),
      Arguments.of(LegalStatus.OTHER, ProbableCause.UNKNOWN),
      Arguments.of(LegalStatus.UNKNOWN, ProbableCause.UNKNOWN),
      Arguments.of(LegalStatus.IMMIGRATION_DETAINEE, ProbableCause.IMMIGRATION_DETAINEE),
    )
  }
}
