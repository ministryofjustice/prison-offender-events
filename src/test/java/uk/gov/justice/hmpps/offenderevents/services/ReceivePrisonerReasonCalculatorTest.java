package uk.gov.justice.hmpps.offenderevents.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.Reason;

import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceivePrisonerReasonCalculatorTest {
    @Mock
    private PrisonApiService prisonApiService;

    private ReceivePrisonerReasonCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ReceivePrisonerReasonCalculator(prisonApiService);
    }

    @Test
    @DisplayName("reason is recall of both legal status RECALL and calculated recall true")
    void reasonIsRecallOfBothLegalStatusRECALLAndCalculatedRecallTrue() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true));

        assertThat(calculator.calculateReasonForPrisoner("A1234GH")).isEqualTo(Reason.RECALL);
    }

    @Test
    @DisplayName("reason is recall if only calculated recall is true")
    void reasonIsRecallIfOnlyCalculatedRecallIsTrue() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("UNKNOWN", true));

        assertThat(calculator.calculateReasonForPrisoner("A1234GH")).isEqualTo(Reason.RECALL);
    }

    @Test
    @DisplayName("TAP movement type takes precedence over recall")
    void tAPMovementTypeTakesPrecedenceOverRecall() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true, "ADM"));
        assertThat(calculator.calculateReasonForPrisoner("A1234GH")).isEqualTo(Reason.RECALL);

        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true, "TAP"));
        assertThat(calculator.calculateReasonForPrisoner("A1234GH")).isEqualTo(Reason.TEMPORARY_ABSENCE_RETURN);
    }

    @Test
    @DisplayName("reason in UNKNOWN when legal status is UNKNOWN and recall is false")
    void reasonInUNKNOWNWhenLegalStatusIsUNKNOWNAndRecallIsFalse() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("UNKNOWN", false));

        assertThat(calculator.calculateReasonForPrisoner("A1234GH")).isEqualTo(Reason.UNKNOWN);
    }

    @Test
    @DisplayName("reason is REMAND when legal status is REMAND")
    void reasonIsREMANDWhenLegalStatusIsREMAND() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("REMAND", false));

        assertThat(calculator.calculateReasonForPrisoner("A1234GH")).isEqualTo(Reason.REMAND);
    }

    @ParameterizedTest
    @MethodSource("legalStatusMap")
    @DisplayName("legal status is mapped to reason")
    void legalStatusIsMappedToReason(LegalStatus legalStatus, Reason reason) {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails(legalStatus.name(), false));

        assertThat(calculator.calculateReasonForPrisoner("A1234GH")).isEqualTo(reason);
    }

    @SuppressWarnings("unused")
    private static Stream<Arguments> legalStatusMap() {
        return Stream.of(
            Arguments.of(LegalStatus.INDETERMINATE_SENTENCE, Reason.CONVICTED),
            Arguments.of(LegalStatus.SENTENCED, Reason.CONVICTED),
            Arguments.of(LegalStatus.CIVIL_PRISONER, Reason.CONVICTED),
            Arguments.of(LegalStatus.CONVICTED_UNSENTENCED, Reason.CONVICTED),
            Arguments.of(LegalStatus.DEAD, Reason.UNKNOWN),
            Arguments.of(LegalStatus.OTHER, Reason.UNKNOWN),
            Arguments.of(LegalStatus.UNKNOWN, Reason.UNKNOWN),
            Arguments.of(LegalStatus.IMMIGRATION_DETAINEE, Reason.IMMIGRATION_DETAINEE)
        );
    }




    private PrisonerDetails prisonerDetails(String legalStatus, boolean recall) {
        return new PrisonerDetails(LegalStatus.valueOf(LegalStatus.class, legalStatus), recall, "ADM");
    }

    private PrisonerDetails prisonerDetails(String legalStatus, boolean recall, String lastMovementTypCode) {
        return new PrisonerDetails(LegalStatus.valueOf(LegalStatus.class, legalStatus), recall, lastMovementTypCode);
    }

}
