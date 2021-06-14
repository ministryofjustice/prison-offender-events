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

    public RecallReason calculateMostLikelyReasonForPrisoner(String offenderNumber) {
        final var prisonerDetails = prisonApiService.getPrisonerDetails(offenderNumber);

        if (prisonerDetails.typeOfMovement() == MovementType.TEMPORARY_ABSENCE_RETURN) {
            return new RecallReason(Reason.TEMPORARY_ABSENCE_RETURN, Source.PRISON);
        }
        if (prisonerDetails.recall()) {
            return new RecallReason(Reason.RECALL, Source.PRISON);
        }

        final Optional<RecallReason> maybeRecallStatusFromProbation = switch (prisonerDetails.legalStatus()) {
            case OTHER, UNKNOWN, CONVICTED_UNSENTENCED, SENTENCED, INDETERMINATE_SENTENCE -> calculateReasonForPrisonerFromProbationOrEmpty(offenderNumber);
            default -> Optional.empty();
        };

        return maybeRecallStatusFromProbation.orElseGet(() -> {
            final var reason = switch (prisonerDetails.legalStatus()) {
                case RECALL -> Reason.RECALL;
                case CIVIL_PRISONER, CONVICTED_UNSENTENCED, SENTENCED, INDETERMINATE_SENTENCE -> Reason.CONVICTED;
                case IMMIGRATION_DETAINEE -> Reason.IMMIGRATION_DETAINEE;
                case REMAND -> Reason.REMAND;
                case DEAD, OTHER, UNKNOWN -> Reason.UNKNOWN;
            };
            return new RecallReason(reason, Source.PRISON);
        });
    }

    private Optional<RecallReason> calculateReasonForPrisonerFromProbationOrEmpty(String offenderNumber) {
        final var maybeRecallList = communityApiService.getRecalls(offenderNumber);
        return maybeRecallList
            .filter(recalls -> recalls.stream().anyMatch(this::hasActiveOrCompletedRecall))
            .map(recalls -> new RecallReason(Reason.RECALL,
                Source.PROBATION,
                String.format("Recall referral date %s", latestRecallReferralDate(maybeRecallList.get()))));
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
        TEMPORARY_ABSENCE_RETURN
    }

    enum Source {
        PRISON,
        PROBATION
    }

    record RecallReason(Reason reason, Source source, String details) {
        public RecallReason(Reason reason, Source source) {
            this(reason, source, null);
        }
    }
}
