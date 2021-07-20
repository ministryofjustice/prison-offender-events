package uk.gov.justice.hmpps.offenderevents.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.function.Predicate.not;

@Component
@Slf4j
public class ReceivePrisonerReasonCalculator {
    private final PrisonApiService prisonApiService;
    private final CommunityApiService communityApiService;

    public ReceivePrisonerReasonCalculator(PrisonApiService prisonApiService, CommunityApiService communityApiService) {
        this.prisonApiService = prisonApiService;
        this.communityApiService = communityApiService;
    }

    public ReceiveReason calculateMostLikelyReasonForPrisonerReceive(String offenderNumber) {
        final var prisonerDetails = prisonApiService.getPrisonerDetails(offenderNumber);
        final var details = String.format("%s:%s", prisonerDetails.status(), prisonerDetails.statusReason());
        final var currentLocation = prisonerDetails.currentLocation();
        final var currentPrisonStatus = prisonerDetails.currentPrisonStatus();
        final var prisonId = prisonerDetails.latestLocationId();
        final Function<Reason, ReasonWithDetailsAndSource> withDetailsAndSource = (reason) -> new ReasonWithDetailsAndSource(reason, Source.PRISON, details);

        final var reasonWithSourceAndDetails = Optional
            .ofNullable(
                switch (prisonerDetails.typeOfMovement()) {
                    case TEMPORARY_ABSENCE -> withDetailsAndSource.apply(Reason.TEMPORARY_ABSENCE_RETURN);
                    case COURT -> withDetailsAndSource.apply(Reason.RETURN_FROM_COURT);
                    case ADMISSION -> switch (prisonerDetails.movementReason()) {
                        case TRANSFER -> withDetailsAndSource.apply(Reason.TRANSFERRED);
                        case RECALL -> withDetailsAndSource.apply(Reason.RECALL);
                        case REMAND -> calculateReasonForPrisonerFromProbationOrEmpty(offenderNumber, details)
                            .orElse(withDetailsAndSource.apply(Reason.REMAND));
                        default -> null;
                    };
                    default -> null;
                })
            .or(() -> Optional
                .of(withDetailsAndSource.apply(Reason.RECALL))
                .filter(notUsed -> prisonerDetails.recall()))
            .or(() ->
                switch (prisonerDetails.legalStatus()) {
                    case OTHER, UNKNOWN, CONVICTED_UNSENTENCED, SENTENCED, INDETERMINATE_SENTENCE -> calculateReasonForPrisonerFromProbationOrEmpty(offenderNumber, details);
                    default -> Optional.empty();
                })
            .orElseGet(() -> {
                final var reason =
                    switch (prisonerDetails.legalStatus()) {
                        case RECALL -> Reason.RECALL;
                        case CIVIL_PRISONER, CONVICTED_UNSENTENCED, SENTENCED, INDETERMINATE_SENTENCE -> Reason.CONVICTED;
                        case IMMIGRATION_DETAINEE -> Reason.IMMIGRATION_DETAINEE;
                        case REMAND -> Reason.REMAND;
                        case DEAD, OTHER, UNKNOWN -> Reason.UNKNOWN;
                    };
                return new ReasonWithDetailsAndSource(reason, Source.PRISON, details);
            });

        return new ReceiveReason(reasonWithSourceAndDetails.reason(),
            reasonWithSourceAndDetails.source(),
            reasonWithSourceAndDetails.details(),
            currentLocation,
            currentPrisonStatus,
            prisonId);

    }

    private Optional<ReasonWithDetailsAndSource> calculateReasonForPrisonerFromProbationOrEmpty(String offenderNumber,
                                                                                                String details) {
        // be lenient with current movement in case the event has been delayed for few days due to a system issue
        final Predicate<Movement> excludingCurrentMovement = movement -> movement
            .movementDate()
            .isBefore(LocalDate.now().minusDays(4));
        final Predicate<LocalDate> hasBeenInPrisonSince = (referralDate) -> {
            final var movements = prisonApiService.getMovements(offenderNumber);
            final var lastPrisonEntryDate = movements
                .stream()
                .filter(movement -> "IN".equals(movement.directionCode()))
                .filter(excludingCurrentMovement)
                .filter(movement -> movement.movementDate().isAfter(referralDate) || movement
                    .movementDate()
                    .equals(referralDate))
                .findAny();

            lastPrisonEntryDate.ifPresent((date) -> log.debug("Last time in prison was {}", date));

            return lastPrisonEntryDate.isPresent();
        };

        final var recalls = communityApiService.getRecalls(offenderNumber).orElse(List.of());
        return recalls
            .stream()
            .filter(this::hasActiveOrCompletedRecall)
            .map(Recall::referralDate)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .filter(not(hasBeenInPrisonSince))
            .map(referralDate -> new ReasonWithDetailsAndSource(Reason.RECALL, Source.PROBATION, String.format("%s Recall referral date %s", details, referralDate)));

    }

    private boolean hasActiveOrCompletedRecall(Recall recall) {
        if (recall.outcomeRecall() != null) {
            return recall.outcomeRecall();
        }

        if (recall.recallRejectedOrWithdrawn() != null) {
            return !recall.recallRejectedOrWithdrawn();
        }
        return false;
    }

    enum Reason {
        RECALL,
        REMAND,
        CONVICTED,
        IMMIGRATION_DETAINEE,
        UNKNOWN,
        TEMPORARY_ABSENCE_RETURN,
        RETURN_FROM_COURT,
        TRANSFERRED
    }

    enum Source {
        PRISON,
        PROBATION
    }

    record ReasonWithDetailsAndSource(Reason reason, Source source, String details) {

    }

    record ReceiveReason(Reason reason, Source source, String details, CurrentLocation currentLocation,
                         CurrentPrisonStatus currentPrisonStatus, String prisonId) implements PrisonerMovementReason {
        public ReceiveReason(Reason reason, Source source, CurrentLocation currentLocation, CurrentPrisonStatus currentPrisonStatus, String prisonId) {
            this(reason, source, null, currentLocation, currentPrisonStatus, prisonId);
        }

        public boolean hasPrisonerActuallyBeenReceived() {
            return currentLocation == CurrentLocation.IN_PRISON;
        }
    }
}
