package uk.gov.justice.hmpps.offenderevents.services;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class ReceivePrisonerReasonCalculator {
    private final PrisonApiService prisonApiService;
    private final CommunityApiService communityApiService;

    public ReceivePrisonerReasonCalculator(PrisonApiService prisonApiService, CommunityApiService communityApiService) {
        this.prisonApiService = prisonApiService;
        this.communityApiService = communityApiService;
    }

    public RecallReason calculateMostLikelyReasonForPrisonerReceive(String offenderNumber) {
        final var prisonerDetails = prisonApiService.getPrisonerDetails(offenderNumber);
        final var details = String.format("%s:%s", prisonerDetails.status(), prisonerDetails.statusReason());
        final var currentLocation = prisonerDetails.currentLocation();
        final var currentPrisonStatus = prisonerDetails.currentPrisonStatus();
        final var prisonId = prisonerDetails.latestLocationId();

        final var reasonWithSourceAndDetails = Optional
            .ofNullable(
                switch (prisonerDetails.typeOfMovement()) {
                    case TEMPORARY_ABSENCE -> Reason.TEMPORARY_ABSENCE_RETURN;
                    case COURT -> Reason.RETURN_FROM_COURT;
                    case ADMISSION -> switch (prisonerDetails.movementReason()) {
                        case TRANSFER -> Reason.TRANSFERRED;
                        case RECALL -> Reason.RECALL;
                        default -> null;
                    };
                    default -> null;
                })
            .or(() -> Optional.of(Reason.RECALL).filter(notUsed -> prisonerDetails.recall()))
            .map(reason -> new ReasonWithDetailsAndSource(reason, Source.PRISON, details))
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

        return new RecallReason(reasonWithSourceAndDetails.reason(),
            reasonWithSourceAndDetails.source(),
            reasonWithSourceAndDetails.details(),
            currentLocation,
            currentPrisonStatus,
            prisonId);

    }

    private Optional<ReasonWithDetailsAndSource> calculateReasonForPrisonerFromProbationOrEmpty(String offenderNumber,
                                                                                                String details) {
        final var maybeRecallList = communityApiService.getRecalls(offenderNumber);
        return maybeRecallList
            .filter(recalls -> recalls.stream().anyMatch(this::hasActiveOrCompletedRecall))
            .map(recalls -> new ReasonWithDetailsAndSource(Reason.RECALL,
                Source.PROBATION,
                String.format("%s Recall referral date %s", details, latestRecallReferralDate(maybeRecallList.get()))));
    }

    private String latestRecallReferralDate(List<Recall> recalls) {
        return recalls
            .stream()
            .filter(this::hasActiveOrCompletedRecall)
            .map(Recall::referralDate)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .map(LocalDate::toString)
            .orElse("unknown");
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

    record RecallReason(Reason reason, Source source, String details, CurrentLocation currentLocation,
                        CurrentPrisonStatus currentPrisonStatus, String prisonId) {
        public RecallReason(Reason reason, Source source, CurrentLocation currentLocation, CurrentPrisonStatus currentPrisonStatus, String prisonId) {
            this(reason, source, null, currentLocation, currentPrisonStatus, prisonId);
        }
    }
}
