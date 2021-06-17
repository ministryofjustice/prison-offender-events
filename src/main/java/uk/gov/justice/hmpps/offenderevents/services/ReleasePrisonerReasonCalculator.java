package uk.gov.justice.hmpps.offenderevents.services;

import org.springframework.stereotype.Component;

import static uk.gov.justice.hmpps.offenderevents.services.MovementType.TEMPORARY_ABSENCE;

@Component
public class ReleasePrisonerReasonCalculator {
    private final PrisonApiService prisonApiService;

    public ReleasePrisonerReasonCalculator(PrisonApiService prisonApiService) {
        this.prisonApiService = prisonApiService;
    }


    public ReleaseReason calculateReasonForRelease(String offenderNumber) {
        final var prisonerDetails = prisonApiService.getPrisonerDetails(offenderNumber);

        return switch (prisonerDetails.typeOfMovement()) {
            case TEMPORARY_ABSENCE -> new ReleaseReason(Reason.TEMPORARY_ABSENCE_RELEASE);
            case COURT -> new ReleaseReason(Reason.SENT_TO_COURT);
            case TRANSFER -> new ReleaseReason(Reason.TRANSFERRED);
            case RELEASED -> {
                final var reason = switch (prisonerDetails.movementReason()) {
                    case HOSPITALISATION -> Reason.RELEASED_TO_HOSPITAL;
                    default -> Reason.UNKNOWN;
                };
                yield new ReleaseReason(reason, String.format("Movement reason code %s",  prisonerDetails.lastMovementReasonCode()));
            }
            default -> new ReleaseReason(Reason.UNKNOWN, String.format("Movement type code %s",  prisonerDetails.lastMovementTypeCode()));
        };
    }

    enum Reason {
        UNKNOWN,
        TEMPORARY_ABSENCE_RELEASE,
        RELEASED_TO_HOSPITAL,
        SENT_TO_COURT,
        TRANSFERRED
    }

    record ReleaseReason(Reason reason, String details) {
        public ReleaseReason(Reason reason) {
            this(reason, null);
        }
    }
}
