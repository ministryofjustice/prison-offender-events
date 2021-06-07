package uk.gov.justice.hmpps.offenderevents.services;

import org.springframework.stereotype.Component;

@Component
public class ReceivePrisonerReasonCalculator {
    private final PrisonApiService prisonApiService;

    public ReceivePrisonerReasonCalculator(PrisonApiService prisonApiService) {
        this.prisonApiService = prisonApiService;
    }

    public Reason calculateReasonForPrisoner(String offenderNumber) {
        final var prisonerDetails = prisonApiService.getPrisonerDetails(offenderNumber);

        if (prisonerDetails.typeOfMovement() == MovementType.TEMPORARY_ABSENCE_RETURN) {
            return Reason.TEMPORARY_ABSENCE_RETURN;
        }
        if (prisonerDetails.recall()) {
            return Reason.RECALL;
        }

        return switch (prisonerDetails.legalStatus()) {
            case RECALL -> Reason.RECALL;
            case CIVIL_PRISONER, CONVICTED_UNSENTENCED, SENTENCED, INDETERMINATE_SENTENCE -> Reason.CONVICTED;
            case IMMIGRATION_DETAINEE -> Reason.IMMIGRATION_DETAINEE;
            case REMAND -> Reason.REMAND;
            case DEAD, OTHER, UNKNOWN -> Reason.UNKNOWN;
        };
    }

    enum Reason {
        RECALL,
        REMAND,
        CONVICTED,
        IMMIGRATION_DETAINEE,
        UNKNOWN,
        TEMPORARY_ABSENCE_RETURN
    }

}
