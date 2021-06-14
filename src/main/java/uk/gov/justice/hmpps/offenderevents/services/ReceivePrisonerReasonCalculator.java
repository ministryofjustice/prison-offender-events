package uk.gov.justice.hmpps.offenderevents.services;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ReceivePrisonerReasonCalculator {
    private final PrisonApiService prisonApiService;
    private final CommunityApiService communityApiService;

    public ReceivePrisonerReasonCalculator(PrisonApiService prisonApiService, CommunityApiService communityApiService) {
        this.prisonApiService = prisonApiService;
        this.communityApiService = communityApiService;
    }

    public Reason calculateReasonForPrisoner(String offenderNumber) {
        final var prisonerDetails = prisonApiService.getPrisonerDetails(offenderNumber);

        if (prisonerDetails.typeOfMovement() == MovementType.TEMPORARY_ABSENCE_RETURN) {
            return Reason.TEMPORARY_ABSENCE_RETURN;
        }
        if (prisonerDetails.recall()) {
            return Reason.RECALL;
        }

        final Optional<Reason> maybeRecallStatusFromProbation = switch (prisonerDetails.legalStatus()) {
            case OTHER, UNKNOWN, CONVICTED_UNSENTENCED, SENTENCED, INDETERMINATE_SENTENCE -> calculateReasonForPrisonerFromProbation(offenderNumber);
            default -> Optional.empty();
        };

        return maybeRecallStatusFromProbation.orElseGet(() -> switch (prisonerDetails.legalStatus()) {
            case RECALL -> Reason.RECALL;
            case CIVIL_PRISONER, CONVICTED_UNSENTENCED, SENTENCED, INDETERMINATE_SENTENCE -> Reason.CONVICTED;
            case IMMIGRATION_DETAINEE -> Reason.IMMIGRATION_DETAINEE;
            case REMAND -> Reason.REMAND;
            case DEAD, OTHER, UNKNOWN -> Reason.UNKNOWN;
        });
    }

    private Optional<Reason> calculateReasonForPrisonerFromProbation(String offenderNumber) {
        final var maybeRecallList = communityApiService.getRecalls(offenderNumber);
        return maybeRecallList
            .filter(recalls -> recalls.stream().anyMatch(this::hasActiveOrCompletedRecall))
            .map(recalls -> Reason.RECALL);
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
        TEMPORARY_ABSENCE_RETURN
    }

}
