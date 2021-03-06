package uk.gov.justice.hmpps.offenderevents.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.Reason;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReleasePrisonerReasonCalculatorTest {
    @Mock
    private PrisonApiService prisonApiService;

    private ReleasePrisonerReasonCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ReleasePrisonerReasonCalculator(prisonApiService);
    }

    @Test
    @DisplayName("when last movement is TAP reason is temporary absence")
    void whenLastMovementIsTAPReasonIsTemporaryAbsence() {
        when(prisonApiService.getPrisonerDetails("A1234GF")).thenReturn(prisonerDetails("TAP"));

        final var reason = calculator.calculateReasonForRelease("A1234GF");

        assertThat(reason.reason()).isEqualTo(Reason.TEMPORARY_ABSENCE_RELEASE);
    }

    @Test
    @DisplayName("when last movement is CRT then reason is sent to court")
    void whenLastMovementIsCRTThenReasonIsSentToCourt() {
        when(prisonApiService.getPrisonerDetails("A1234GF")).thenReturn(prisonerDetails("CRT"));

        final var reason = calculator.calculateReasonForRelease("A1234GF");

        assertThat(reason.reason()).isEqualTo(Reason.SENT_TO_COURT);
    }

    @Test
    @DisplayName("when last movement is a TRN then the reason is transferred")
    void whenLastMovementIsATRNThenTheReasonIsTransfer() {
        when(prisonApiService.getPrisonerDetails("A1234GF")).thenReturn(prisonerDetails("TRN"));

        final var reason = calculator.calculateReasonForRelease("A1234GF");

        assertThat(reason.reason()).isEqualTo(Reason.TRANSFERRED);
    }


    @Test
    @DisplayName("when release to hospital reason is hospital release")
    void whenReleaseToHospitalReasonIsHospitalRelease() {
        when(prisonApiService.getPrisonerDetails("A1234GF")).thenReturn(prisonerDetails("REL", "HP"));

        final var reason = calculator.calculateReasonForRelease("A1234GF");

        assertThat(reason.reason()).isEqualTo(Reason.RELEASED_TO_HOSPITAL);
    }

    @Test
    @DisplayName("will add movement type to reason when we do not know the type")
    void willAddMovementTypeToReasonWhenWeDoNotKnowTheType() {
        when(prisonApiService.getPrisonerDetails("A1234GF")).thenReturn(prisonerDetails("XXX"));

        final var reason = calculator.calculateReasonForRelease("A1234GF");

        assertThat(reason.reason()).isEqualTo(Reason.UNKNOWN);
        assertThat(reason.details()).isEqualTo("Movement type code XXX");

    }

    @Test
    @DisplayName("will add movement reason codes when we do not know reason")
    void willAddMovementReasonCodesWhenWeDoNotKnowReason() {
        when(prisonApiService.getPrisonerDetails("A1234GF")).thenReturn(prisonerDetails("REL", "YY"));

        final var reason = calculator.calculateReasonForRelease("A1234GF");

        assertThat(reason.reason()).isEqualTo(Reason.RELEASED);
        assertThat(reason.details()).isEqualTo("Movement reason code YY");

    }

    @Test
    @DisplayName("when status indicates still in prison then not really released")
    void whenStatusIndicatesStillInPrisonThenNotReallyReleased() {
        when(prisonApiService.getPrisonerDetails(any()))
            .thenReturn(new PrisonerDetails(LegalStatus.valueOf(LegalStatus.class, "REMAND"),
                false,
                "TRN",
                "P",
                "INACTIVE OUT",
                "TRN-P",
                "MDI"));
        assertThat(calculator.calculateReasonForRelease("A1234GH").hasPrisonerActuallyBeenRelease()).isTrue();

        when(prisonApiService.getPrisonerDetails(any()))
            .thenReturn(new PrisonerDetails(LegalStatus.valueOf(LegalStatus.class, "REMAND"),
                false,
                "REL",
                "P",
                "INACTIVE OUT",
                "TRN-P",
                "MDI"));
        assertThat(calculator.calculateReasonForRelease("A1234GH").hasPrisonerActuallyBeenRelease()).isTrue();


        when(prisonApiService.getPrisonerDetails(any()))
            .thenReturn(new PrisonerDetails(LegalStatus.valueOf(LegalStatus.class, "REMAND"),
                false,
                "ADM",
                "L",
                "ACTIVE IN",
                "ADM-L",
                "MDI"));
        assertThat(calculator.calculateReasonForRelease("A1234GH").hasPrisonerActuallyBeenRelease()).isFalse();
    }


    private PrisonerDetails prisonerDetails(String lastMovementTypCode, String lastMovementReasonCode) {
        return new PrisonerDetails(LegalStatus.SENTENCED, false, lastMovementTypCode, lastMovementReasonCode, "ACTIVE OUT", String
            .format("%s-%s", lastMovementTypCode, lastMovementReasonCode), "MDI");
    }

    private PrisonerDetails prisonerDetails(String lastMovementTypCode) {
        return prisonerDetails(lastMovementTypCode, "N");
    }

}
