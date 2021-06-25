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
        final var currentLocation = prisonerDetails.currentLocation();
        final var currentPrisonStatus = prisonerDetails.currentPrisonStatus();
        final var lastLocationId = prisonerDetails.lastLocationId();

        final var reasonWithDetails =
            switch (prisonerDetails.typeOfMovement()) {
                case TEMPORARY_ABSENCE -> new ReasonWithDetails(Reason.TEMPORARY_ABSENCE_RELEASE);
                case COURT -> new ReasonWithDetails(Reason.SENT_TO_COURT);
                case TRANSFER -> new ReasonWithDetails(Reason.TRANSFERRED);
                case RELEASED -> {
                    final var reason = switch (prisonerDetails.movementReason()) {
                        case HOSPITALISATION -> Reason.RELEASED_TO_HOSPITAL;
                        default -> Reason.RELEASED;
                    };
                    yield new ReasonWithDetails(reason, String.format("Movement reason code %s", prisonerDetails.lastMovementReasonCode()));
                }
                default -> new ReasonWithDetails(Reason.UNKNOWN, String.format("Movement type code %s", prisonerDetails.lastMovementTypeCode()));
            };

        return new ReleaseReason(reasonWithDetails.reason(), reasonWithDetails.details(), currentLocation, currentPrisonStatus, lastLocationId);
    }

    enum Reason {
        UNKNOWN,
        TEMPORARY_ABSENCE_RELEASE,
        RELEASED_TO_HOSPITAL,
        RELEASED,
        SENT_TO_COURT,
        TRANSFERRED
    }

    record ReasonWithDetails(Reason reason, String details) {
        public ReasonWithDetails(Reason reason) {
            this(reason, null);
        }
    }

    record ReleaseReason(Reason reason, String details, CurrentLocation currentLocation, CurrentPrisonStatus currentPrisonStatus, String lastLocationId) {
        public ReleaseReason(Reason reason, CurrentLocation currentLocation, CurrentPrisonStatus currentPrisonStatus, String lastLocationId) {
            this(reason, null, currentLocation, currentPrisonStatus, lastLocationId);
        }
    }
}
