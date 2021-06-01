package uk.gov.justice.hmpps.offenderevents.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.Reason;

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
    @DisplayName("reason in UNKNOWN when legal status is UNKNOWN and recall is false")
    void reasonInUNKNOWNWhenLegalStatusIsUNKNOWNAndRecallIsFalse() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("UNKNOWN", false));

        assertThat(calculator.calculateReasonForPrisoner("A1234GH")).isEqualTo(Reason.UNKNOWN);
    }


    private PrisonerDetails prisonerDetails(String legalStatus, boolean recall) {
        return new PrisonerDetails(legalStatus, recall);
    }

}
