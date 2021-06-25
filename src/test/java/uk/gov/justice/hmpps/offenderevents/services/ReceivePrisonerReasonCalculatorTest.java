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
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.TEMPORARY_ABSENCE_RETURN);
    }

    @Test
    @DisplayName("COURT movement type takes precedence over recall")
    void courtMovementTypeTakesPrecedenceOverRecall() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true, "ADM"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.RECALL);

        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true, "CRT"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.RETURN_FROM_COURT);
    }

    @Test
    @DisplayName("movement reason of INT means reason is a TRANSFER")
    void movementReasonOfINTMeansReasonIsATRANSFER() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true, "ADM", "L"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.RECALL);

        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("RECALL", true, "ADM", "INT"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.TRANSFERRED);
    }

    @Test
    @DisplayName("movement reason of L (Licence Revokee) means reason is a RECALL")
    void movementReasonOfLMeansReasonIsARECALL() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("SENTENCED", false, "ADM", "V"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.CONVICTED);

        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("SENTENCED", false, "ADM", "L"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.RECALL);
    }
    @Test
    @DisplayName("movement reason of B (HDC Recall) means reason is a RECALL")
    void movementReasonOfBMeansReasonIsARECALL() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("SENTENCED", false, "ADM", "V"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.CONVICTED);

        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("SENTENCED", false, "ADM", "B"));
        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.RECALL);
    }


    @Test
    @DisplayName("reason in UNKNOWN when legal status is UNKNOWN and recall is false")
    void reasonInUNKNOWNWhenLegalStatusIsUNKNOWNAndRecallIsFalse() {
        when(communityApiService.getRecalls(any())).thenReturn(Optional.empty());
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("UNKNOWN", false));

        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.UNKNOWN);
    }

    @Test
    @DisplayName("reason is REMAND when legal status is REMAND")
    void reasonIsREMANDWhenLegalStatusIsREMAND() {
        when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails("REMAND", false));

        assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(Reason.REMAND);
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

    private PrisonerDetails prisonerDetails(String legalStatus, boolean recall) {
        return prisonerDetails(legalStatus, recall, "ADM", "N");
    }

    private PrisonerDetails prisonerDetails(String legalStatus, boolean recall, String lastMovementTypeCode) {
        return prisonerDetails(legalStatus, recall, lastMovementTypeCode, "N");
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

                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(reason);
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

                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(reason);
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
            }

            @ParameterizedTest
            @MethodSource("legalStatusMap")
            @DisplayName("legal status is mapped to reason")
            @MockitoSettings(strictness = Strictness.LENIENT)
            void legalStatusIsMappedToReason(LegalStatus legalStatus, Reason reason) {
                when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails(legalStatus.name(), false));

                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(reason);
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

                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(reason);
            }

            @Test
            @DisplayName("reason source will be prison")
            void reasonSourceWillBeProbationWhenOverridden() {
                when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails(LegalStatus.SENTENCED.name(), false));
                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").source()).isEqualTo(Source.PRISON);

                when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails(LegalStatus.IMMIGRATION_DETAINEE.name(), false));
                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").source()).isEqualTo(Source.PRISON);
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

                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(reason);
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
            }

            @ParameterizedTest
            @MethodSource("legalStatusMap")
            @DisplayName("legal status is mapped to reason")
            @MockitoSettings(strictness = Strictness.LENIENT)
            void legalStatusIsMappedToReason(LegalStatus legalStatus, Reason reason) {
                when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails(legalStatus.name(), false));

                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").reason()).isEqualTo(reason);
            }

            @Test
            @DisplayName("reason source will be probation when overridden")
            void reasonSourceWillBeProbationWhenOverridden() {
                when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails(LegalStatus.SENTENCED.name(), false));
                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").source()).isEqualTo(Source.PROBATION);

                when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails(LegalStatus.IMMIGRATION_DETAINEE.name(), false));
                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").source()).isEqualTo(Source.PRISON);
            }

            @Test
            @DisplayName("will add a further information message when overridden")
            void willAddAFurtherInformationMessageWhenOverridden() {
                when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails(LegalStatus.SENTENCED.name(), false));
                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").details()).isEqualTo("ACTIVE IN:ADM-N Recall referral date 2021-06-13");

                when(prisonApiService.getPrisonerDetails(any())).thenReturn(prisonerDetails(LegalStatus.IMMIGRATION_DETAINEE.name(), false));
                assertThat(calculator.calculateMostLikelyReasonForPrisonerReceive("A1234GH").details()).isEqualTo("ACTIVE IN:ADM-N");
            }
        }
    }
}
