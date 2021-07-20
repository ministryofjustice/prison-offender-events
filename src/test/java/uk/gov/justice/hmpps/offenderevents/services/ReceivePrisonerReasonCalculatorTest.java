package uk.gov.justice.hmpps.offenderevents.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.Reason;
import uk.gov.justice.hmpps.offenderevents.services.ReceivePrisonerReasonCalculator.Source;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceivePrisonerReasonCalculatorTest {
    @Mock
    private PrisonApiService prisonApiService;
    @Mock
    private CommunityApiService communityApiService;

    private ReceivePrisonerReasonCalculator calculator;

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

    @BeforeEach
    void setUp() {
        calculator = new ReceivePrisonerReasonCalculator(prisonApiService, communityApiService);
    }

    @Test
    @DisplayName("reason is recall of both legal status RECALL and calculated recall true")
    void reasonIsRecallIfBothLegalStatusRECALLAndCalculatedRecallTrue() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true));

        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.RECALL);
    }

    @Test
    @DisplayName("reason is recall if only calculated recall is true")
    void reasonIsRecallIfOnlyCalculatedRecallIsTrue() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("UNKNOWN", true));

        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.RECALL);
    }

    @Test
    @DisplayName("TAP movement type takes precedence over recall")
    void tAPMovementTypeTakesPrecedenceOverRecall() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true, "ADM"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.RECALL);

        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true, "TAP"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
            .isEqualTo(Reason.TEMPORARY_ABSENCE_RETURN);
    }

    @Test
    @DisplayName("COURT movement type takes precedence over recall")
    void courtMovementTypeTakesPrecedenceOverRecall() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true, "ADM"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.RECALL);

        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true, "CRT"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
            .isEqualTo(Reason.RETURN_FROM_COURT);
    }

    @Test
    @DisplayName("movement reason of INT means reason is a TRANSFER")
    void movementReasonOfINTMeansReasonIsATRANSFER() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true, "ADM", "L"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.RECALL);

        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true, "ADM", "INT"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
            .isEqualTo(Reason.TRANSFERRED);

    }

    @Test
    @DisplayName("movement reason of TRNCRT (transfer via court) means reason is a TRANSFER")
    void movementReasonOfTRNCRTMeansReasonIsATRANSFER() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true, "ADM", "L"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.RECALL);

        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true, "ADM", "TRNCRT"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
            .isEqualTo(Reason.TRANSFERRED);
    }

    @Test
    @DisplayName("movement reason of TRNTAP (transfer via TAP) means reason is a TRANSFER")
    void movementReasonOfTRNTAPMeansReasonIsATRANSFER() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true, "ADM", "L"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.RECALL);

        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true, "ADM", "TRNTAP"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
            .isEqualTo(Reason.TRANSFERRED);
    }

    @Test
    @DisplayName("movement reason of L (Licence Revokee) means reason is a RECALL")
    void movementReasonOfLMeansReasonIsARECALL() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("SENTENCED", false, "ADM", "V"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
            .isEqualTo(Reason.CONVICTED);

        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("SENTENCED", false, "ADM", "L"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.RECALL);
    }

    @Test
    @DisplayName("movement reason of Y (Recall from Detention Training Order) means reason is a RECALL")
    void movementReasonOfYMeansReasonIsARECALL() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("SENTENCED", false, "ADM", "V"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
            .isEqualTo(Reason.CONVICTED);

        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("SENTENCED", false, "ADM", "Y"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.RECALL);
    }

    @Test
    @DisplayName("movement reason of B (HDC Recall) means reason is a RECALL")
    void movementReasonOfBMeansReasonIsARECALL() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("SENTENCED", false, "ADM", "V"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
            .isEqualTo(Reason.CONVICTED);

        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("SENTENCED", false, "ADM", "B"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.RECALL);
    }


    @Test
    @DisplayName("movement reason of N (Unconvicted Remand) means reason is a REMAND when no recall NSI")
    void movementReasonOfNMeansReasonIsREMAND() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("SENTENCED", false, "ADM", "V"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.CONVICTED);

        when(communityApiService.getRecalls(any()))
            .thenReturn(Optional.of(List.of()));

        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("SENTENCED", false, "ADM", "N"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.REMAND);
    }

    @Test
    @DisplayName("movement reason of N (Unconvicted Remand) means reason is a RECALL when there is a recall NSI")
    void movementReasonOfNMeansReasonIsRECALLWithRecallNSI() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("SENTENCED", false, "ADM", "V"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
            .isEqualTo(Reason.CONVICTED);

        when(communityApiService.getRecalls(any()))
            .thenReturn(Optional.of(List.of(new Recall(LocalDate.now(), false, null))));
        when(prisonApiService.getMovements(any())).thenReturn(List.of(
            new Movement("IN", LocalDate.now()),
            new Movement("OUT", LocalDate.parse("2021-06-13")),
            new Movement("IN", LocalDate.parse("2021-05-13"))
        ));
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("SENTENCED", false, "ADM", "N"));

        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.RECALL);
    }

    @Test
    @DisplayName("reason in UNKNOWN when legal status is UNKNOWN and recall is false")
    void reasonInUNKNOWNWhenLegalStatusIsUNKNOWNAndRecallIsFalse() {
        when(communityApiService.getRecalls(any())).thenReturn(Optional.empty());
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("UNKNOWN", false));

        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
            .isEqualTo(Reason.UNKNOWN);
    }

    @Test
    @DisplayName("reason is REMAND when legal status is REMAND")
    void reasonIsREMANDWhenLegalStatusIsREMAND() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("REMAND", false));

        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.REMAND);
    }

    @Test
    @DisplayName("when current status indicates not in prison then not really received")
    void whenCurrentStatusIndicatesNotInPrisonThenNotReallyReceived() {
        when(prisonApiService.getPrisonerDetails(any()))
            .thenReturn(new PrisonerDetails(LegalStatus.valueOf(LegalStatus.class, "REMAND"),
                false,
                "ADM",
                "L",
                "ACTIVE IN",
                "ADM-L",
                "MDI"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").hasPrisonerActuallyBeenReceived())
            .isTrue();

        when(prisonApiService.getPrisonerDetails(any()))
            .thenReturn(new PrisonerDetails(LegalStatus.valueOf(LegalStatus.class, "REMAND"),
                false,
                "TRN",
                "P",
                "INACTIVE OUT",
                "TRN-P",
                "MDI"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").hasPrisonerActuallyBeenReceived())
            .isFalse();
    }


    @ParameterizedTest
    @MethodSource("legalStatusMap")
    @DisplayName("legal status is mapped to reason")
    @MockitoSettings(strictness = Strictness.LENIENT)
    void legalStatusIsMappedToReason(LegalStatus legalStatus, Reason reason) {
        when(communityApiService.getRecalls(any())).thenReturn(Optional.empty());
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails(legalStatus.name(), false));

        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(reason);
    }

    @Test
    @DisplayName("missing legal status is treated as unknown")
    void missingLegalStatusIsTreatedAsUnknown() {
        when(communityApiService.getRecalls(any())).thenReturn(Optional.empty());
        when(prisonApiService.getPrisonerDetails(any()))
            .thenReturn(new PrisonerDetails(null, false, "XXX", "XXX", "ACTIVE IN", "XXX-XXX", "MDI"));

        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
            .isEqualTo(Reason.UNKNOWN);
    }

    @Test
    @DisplayName("can still calculate remand when legal status is empty")
    void canStillCalculateRemandWhenLegalStatusIsEmpty() {
        when(prisonApiService.getPrisonerDetails(any()))
            .thenReturn(new PrisonerDetails(null, false, "ADM", "N", "ACTIVE IN", "ADM-N", "MDI"));

        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.REMAND);
    }


    private PrisonerDetails prisonerDetails(String legalStatus, boolean recall) {
        return prisonerDetails(legalStatus, recall, "ADM", "K");
    }

    private PrisonerDetails prisonerDetails(String legalStatus, boolean recall, String lastMovementTypeCode) {
        return prisonerDetails(legalStatus, recall, lastMovementTypeCode, "K");
    }

    private PrisonerDetails prisonerDetails(String legalStatus, boolean recall, String lastMovementTypeCode, String lastMovementReasonCode) {
        return new PrisonerDetails(LegalStatus.valueOf(LegalStatus.class, legalStatus), recall, lastMovementTypeCode, lastMovementReasonCode, "ACTIVE IN", String
            .format("%s-%s", lastMovementTypeCode, lastMovementReasonCode), "MDI");
    }


    @Nested
    @DisplayName("when delegating to probation")
    class WhenDelegatingToProbation {
        @Nested
        @DisplayName("when offender not found")
        class WhenOffenderNotFound {
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

            @BeforeEach
            void setUp() {
                when(communityApiService.getRecalls(any())).thenReturn(Optional.empty());
            }

            @ParameterizedTest
            @MethodSource("legalStatusMap")
            @DisplayName("legal status is mapped to reason")
            @MockitoSettings(strictness = Strictness.LENIENT)
            void legalStatusIsMappedToReason(LegalStatus legalStatus, Reason reason) {
                when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails(legalStatus.name(), false));

                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
                    .isEqualTo(reason);
            }

        }

        @Nested
        @DisplayName("when offender has no recall requests")
        class WhenOffenderHasNoRecallRequest {
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

            @BeforeEach
            void setUp() {
                when(communityApiService.getRecalls(any())).thenReturn(Optional.of(List.of()));
            }

            @ParameterizedTest
            @MethodSource("legalStatusMap")
            @DisplayName("legal status is mapped to reason")
            @MockitoSettings(strictness = Strictness.LENIENT)
            void legalStatusIsMappedToReason(LegalStatus legalStatus, Reason reason) {
                when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails(legalStatus.name(), false));

                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
                    .isEqualTo(reason);
            }
        }

        @Nested
        @DisplayName("when has recall in progress with no outcome")
        class WhenHasRecallInProgressWithNoOutcome {
            private static Stream<Arguments> legalStatusMap() {
                return Stream.of(
                    Arguments.of(LegalStatus.INDETERMINATE_SENTENCE, Reason.RECALL),
                    Arguments.of(LegalStatus.SENTENCED, Reason.RECALL),
                    Arguments.of(LegalStatus.CIVIL_PRISONER, Reason.CONVICTED),
                    Arguments.of(LegalStatus.CONVICTED_UNSENTENCED, Reason.RECALL),
                    Arguments.of(LegalStatus.DEAD, Reason.UNKNOWN),
                    Arguments.of(LegalStatus.OTHER, Reason.RECALL),
                    Arguments.of(LegalStatus.UNKNOWN, Reason.RECALL),
                    Arguments.of(LegalStatus.IMMIGRATION_DETAINEE, Reason.IMMIGRATION_DETAINEE)
                );
            }

            @BeforeEach
            void setUp() {
                when(communityApiService.getRecalls(any()))
                    .thenReturn(Optional.of(List.of(new Recall(LocalDate.now(), false, null))));
                when(prisonApiService.getMovements(any()))
                    .thenReturn(List.of(new Movement("OUT", LocalDate.now().minusYears(99))));
            }

            @ParameterizedTest
            @MethodSource("legalStatusMap")
            @DisplayName("legal status is mapped to reason")
            @MockitoSettings(strictness = Strictness.LENIENT)
            void legalStatusIsMappedToReason(LegalStatus legalStatus, Reason reason) {
                when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails(legalStatus.name(), false));

                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
                    .isEqualTo(reason);
            }

        }

        @Nested
        @DisplayName("when has recall rejected")
        class WhenHasRecallRejected {
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

            @BeforeEach
            void setUp() {
                when(communityApiService.getRecalls(any()))
                    .thenReturn(Optional.of(List.of(new Recall(LocalDate.now(), true, null))));
            }

            @ParameterizedTest
            @MethodSource("legalStatusMap")
            @DisplayName("legal status is mapped to reason")
            @MockitoSettings(strictness = Strictness.LENIENT)
            void legalStatusIsMappedToReason(LegalStatus legalStatus, Reason reason) {
                when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails(legalStatus.name(), false));

                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
                    .isEqualTo(reason);
            }

            @Test
            @DisplayName("reason source will be prison")
            void reasonSourceWillBeProbationWhenOverridden() {
                when(prisonApiService.getPrisonerDetails(any()))
                    .thenReturn(prisonerDetails(LegalStatus.SENTENCED.name(), false));
                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").source())
                    .isEqualTo(Source.PRISON);

                when(prisonApiService.getPrisonerDetails(any()))
                    .thenReturn(prisonerDetails(LegalStatus.IMMIGRATION_DETAINEE.name(), false));
                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").source())
                    .isEqualTo(Source.PRISON);
            }

        }

        @Nested
        @DisplayName("when has recall outcome but not a recall")
        class WhenHasRecallOutcomeButNotRecall {
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

            @BeforeEach
            void setUp() {
                // currently this will not happen, if the outcome is not a recall then it must have been rejected in which case
                // recall would be in rejected state anyway - but tests incase community-api behavior changes
                when(communityApiService.getRecalls(any()))
                    .thenReturn(Optional.of(List.of(new Recall(LocalDate.now(), false, false))));
            }

            @ParameterizedTest
            @MethodSource("legalStatusMap")
            @DisplayName("legal status is mapped to reason")
            @MockitoSettings(strictness = Strictness.LENIENT)
            void legalStatusIsMappedToReason(LegalStatus legalStatus, Reason reason) {
                when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails(legalStatus.name(), false));

                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
                    .isEqualTo(reason);
            }

        }

        @Nested
        @DisplayName("when has recall outcome that is a recall")
        class WhenHasRecallOutcomeIsRecall {
            private static Stream<Arguments> legalStatusMap() {
                return Stream.of(
                    Arguments.of(LegalStatus.INDETERMINATE_SENTENCE, Reason.RECALL),
                    Arguments.of(LegalStatus.SENTENCED, Reason.RECALL),
                    Arguments.of(LegalStatus.CIVIL_PRISONER, Reason.CONVICTED),
                    Arguments.of(LegalStatus.CONVICTED_UNSENTENCED, Reason.RECALL),
                    Arguments.of(LegalStatus.DEAD, Reason.UNKNOWN),
                    Arguments.of(LegalStatus.OTHER, Reason.RECALL),
                    Arguments.of(LegalStatus.UNKNOWN, Reason.RECALL),
                    Arguments.of(LegalStatus.IMMIGRATION_DETAINEE, Reason.IMMIGRATION_DETAINEE)
                );
            }

            @BeforeEach
            void setUp() {
                when(communityApiService.getRecalls(any()))
                    .thenReturn(Optional.of(List.of(new Recall(LocalDate.parse("2021-06-13"), false, true))));
                when(prisonApiService.getMovements(any()))
                    .thenReturn(List.of(new Movement("OUT", LocalDate.now().minusYears(99))));
            }

            @ParameterizedTest
            @MethodSource("legalStatusMap")
            @DisplayName("legal status is mapped to reason")
            @MockitoSettings(strictness = Strictness.LENIENT)
            void legalStatusIsMappedToReason(LegalStatus legalStatus, Reason reason) {
                when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails(legalStatus.name(), false));

                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
                    .isEqualTo(reason);
            }

            @Test
            @DisplayName("reason source will be probation when overridden")
            void reasonSourceWillBeProbationWhenOverridden() {
                when(prisonApiService.getPrisonerDetails(any()))
                    .thenReturn(prisonerDetails(LegalStatus.SENTENCED.name(), false));
                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").source())
                    .isEqualTo(Source.PROBATION);

                when(prisonApiService.getPrisonerDetails(any()))
                    .thenReturn(prisonerDetails(LegalStatus.IMMIGRATION_DETAINEE.name(), false));
                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").source())
                    .isEqualTo(Source.PRISON);
            }

            @Test
            @DisplayName("will add a further information message when overridden")
            void willAddAFurtherInformationMessageWhenOverridden() {
                when(prisonApiService.getPrisonerDetails(any()))
                    .thenReturn(prisonerDetails(LegalStatus.SENTENCED.name(), false));
                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").details())
                    .isEqualTo("ACTIVE IN:ADM-K Recall referral date 2021-06-13");

                when(prisonApiService.getPrisonerDetails(any()))
                    .thenReturn(prisonerDetails(LegalStatus.IMMIGRATION_DETAINEE.name(), false));
                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").details())
                    .isEqualTo("ACTIVE IN:ADM-K");
            }
        }

        @Nested
        @DisplayName("with multiple recall outcomes")
        class WithAMultipleOutcomes {
            @BeforeEach
            void setUp() {
                when(prisonApiService.getPrisonerDetails(any()))
                    .thenReturn(prisonerDetails(LegalStatus.SENTENCED.name(), false));
                when(communityApiService.getRecalls(any()))
                    .thenReturn(Optional.of(List.of(
                        new Recall(LocalDate.parse("2021-06-13"), false, true),
                        new Recall(LocalDate.parse("2020-06-13"), false, true))));
            }

            @Nested
            @DisplayName("with just today's movement")
            class NoPreviousMovements {
                @BeforeEach
                void setUp() {
                    when(prisonApiService.getMovements(any())).thenReturn(List.of(new Movement("IN", LocalDate.now())));
                }

                @Test
                @DisplayName("recall will be treated as current")
                void recallWillBeTreatedAsCurrent() {
                    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
                        .isEqualTo(Reason.RECALL);
                }
            }

            @Nested
            @DisplayName("with just a very recent movement")
            class NoOldishPreviousMovements {
                @BeforeEach
                void setUp() {
                    when(prisonApiService.getMovements(any()))
                        .thenReturn(List.of(new Movement("IN", LocalDate.now().minusDays(2))));
                }

                @Test
                @DisplayName("recall will be treated as current")
                void recallWillBeTreatedAsCurrent() {
                    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
                        .isEqualTo(Reason.RECALL);
                }
            }

            @Nested
            @DisplayName("with today's movement and really old one")
            class NoRecentPreviousMovements {
                @BeforeEach
                void setUp() {
                    when(prisonApiService.getMovements(any())).thenReturn(List.of(
                        new Movement("IN", LocalDate.now()),
                        new Movement("OUT", LocalDate.parse("2010-06-13")),
                        new Movement("IN", LocalDate.parse("2010-05-13"))
                    ));
                }

                @Test
                @DisplayName("recall will be treated as current")
                void recallWillBeTreatedAsCurrent() {
                    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
                        .isEqualTo(Reason.RECALL);
                }
            }

            @Nested
            @DisplayName("with today's movement and one after first recall but before latest recall")
            class PreviousBetweenRecallsMovements {
                @BeforeEach
                void setUp() {
                    when(prisonApiService.getMovements(any())).thenReturn(List.of(
                        new Movement("IN", LocalDate.now()),
                        new Movement("OUT", LocalDate.parse("2021-06-13")),
                        new Movement("IN", LocalDate.parse("2021-05-13"))
                    ));
                }

                @Test
                @DisplayName("recall will be treated as current")
                void recallWillBeTreatedAsCurrent() {
                    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
                        .isEqualTo(Reason.RECALL);
                }
            }

            @Nested
            @DisplayName("with today's movement and one after latest recall")
            class PreviousAfterRecallsMovements {
                @BeforeEach
                void setUp() {
                    when(prisonApiService.getMovements(any())).thenReturn(List.of(
                        new Movement("IN", LocalDate.now()),
                        new Movement("IN", LocalDate.parse("2021-06-13")),
                        new Movement("OUT", LocalDate.parse("2021-06-23")))
                    );
                }

                @Test
                @DisplayName("recall will be ignored")
                void recallWillBeTreatedAsCurrent() {
                    assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason())
                        .isEqualTo(Reason.CONVICTED);
                }
            }
        }
    }
}
