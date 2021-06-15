package uk.gov.justice.hmpps.offenderevents.services;

import org.springframework.stereotype.Component;

@Component
public class ReleasePrisonerReasonCalculator {
    private final PrisonApiService prisonApiService;

    public ReleasePrisonerReasonCalculator(PrisonApiService prisonApiService) {
        this.prisonApiService = prisonApiService;
    }


    public ReleaseReason calculateReasonForRelease(String offenderNumber) {
        final var prisonerDetails = prisonApiService.getPrisonerDetails(offenderNumber);

        if (prisonerDetails.typeOfMovement() == MovementType.TEMPORARY_ABSENCE) {
            return new ReleaseReason(Reason.TEMPORARY_ABSENCE);
        }

        return new ReleaseReason(Reason.UNKNOWN);
    }

    enum Reason {
        UNKNOWN,
        TEMPORARY_ABSENCE
    }

    record ReleaseReason(Reason reason) {
    }
}
