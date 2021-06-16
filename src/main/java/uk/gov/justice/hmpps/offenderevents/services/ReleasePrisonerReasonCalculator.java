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
            return new ReleaseReason(Reason.TEMPORARY_ABSENCE_RELEASE);
        }

        if (prisonerDetails.typeOfMovement() == MovementType.COURT) {
            return new ReleaseReason(Reason.SENT_TO_COURT);
        }

        if (prisonerDetails.typeOfMovement() == MovementType.RELEASED) {
            final var reason = switch (prisonerDetails.movementReason()) {
                case HOSPITALISATION -> Reason.RELEASED_TO_HOSPITAL;
                default -> Reason.UNKNOWN;
            };
            return new ReleaseReason(reason, String.format("Movement reason code %s",  prisonerDetails.lastMovementReasonCode()));
        }

        return new ReleaseReason(Reason.UNKNOWN, String.format("Movement type code %s",  prisonerDetails.lastMovementTypeCode()));
    }

    enum Reason {
        UNKNOWN,
        TEMPORARY_ABSENCE_RELEASE,
        RELEASED_TO_HOSPITAL,
        SENT_TO_COURT
    }

    record ReleaseReason(Reason reason, String details) {
        public ReleaseReason(Reason reason) {
            this(reason, null);
        }
    }
}
