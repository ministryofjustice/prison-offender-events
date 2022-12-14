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
        final var latestLocationId = prisonerDetails.latestLocationId();
        final var nomisMovementReason = new MovementReason(prisonerDetails.lastMovementReasonCode());

        final var reasonWithDetails =
            switch (prisonerDetails.typeOfMovement()) {
                case TEMPORARY_ABSENCE -> new ReasonWithDetails(Reason.TEMPORARY_ABSENCE_RELEASE, new MovementReason(prisonerDetails.lastMovementReasonCode()));
                case COURT -> new ReasonWithDetails(Reason.SENT_TO_COURT, new MovementReason(prisonerDetails.lastMovementReasonCode()));
                case TRANSFER -> new ReasonWithDetails(Reason.TRANSFERRED, new MovementReason(prisonerDetails.lastMovementReasonCode()));
                case RELEASED -> {
                    final var reason = switch (prisonerDetails.movementReason()) {
                        case HOSPITALISATION -> Reason.RELEASED_TO_HOSPITAL;
                        default -> Reason.RELEASED;
                    };
                    yield new ReasonWithDetails(reason, String.format("Movement reason code %s", prisonerDetails.lastMovementReasonCode()), new MovementReason(prisonerDetails.lastMovementReasonCode()));
                }
                default -> new ReasonWithDetails(Reason.UNKNOWN, String.format("Movement type code %s", prisonerDetails.lastMovementTypeCode()), new MovementReason(prisonerDetails.lastMovementReasonCode()));
            };

        return new ReleaseReason(reasonWithDetails.reason(), reasonWithDetails.details(), currentLocation, currentPrisonStatus, latestLocationId, nomisMovementReason);
    }

    enum Reason {
        UNKNOWN,
        TEMPORARY_ABSENCE_RELEASE,
        RELEASED_TO_HOSPITAL,
        RELEASED,
        SENT_TO_COURT,
        TRANSFERRED
    }

    record MovementReason(String code) {
    }

    record ReasonWithDetails(Reason reason, String details, MovementReason nomisMovementReason) {
        public ReasonWithDetails(Reason reason, MovementReason nomisMovementReason) {
            this(reason, null, nomisMovementReason);
        }
    }

    record ReleaseReason(Reason reason, String details, CurrentLocation currentLocation, CurrentPrisonStatus currentPrisonStatus, String prisonId, MovementReason nomisMovementReason) implements PrisonerMovementReason {
        public ReleaseReason(Reason reason, CurrentLocation currentLocation, CurrentPrisonStatus currentPrisonStatus, String prisonId, MovementReason nomisMovementReason) {
            this(reason, null, currentLocation, currentPrisonStatus, prisonId, nomisMovementReason);
        }

        public boolean hasPrisonerActuallyBeenRelease() {
            return currentLocation != CurrentLocation.IN_PRISON;
        }
    }
}
