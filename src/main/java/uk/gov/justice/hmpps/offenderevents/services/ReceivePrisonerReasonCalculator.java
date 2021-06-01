package uk.gov.justice.hmpps.offenderevents.services;

import org.springframework.stereotype.Component;

@Component
public class ReceivePrisonerReasonCalculator {
    enum Reason {
        RECALL,
        UNKNOWN
    }
    private final PrisonApiService prisonApiService;

    public ReceivePrisonerReasonCalculator(PrisonApiService prisonApiService) {
        this.prisonApiService = prisonApiService;
    }

    public Reason calculateReasonForPrisoner(String offenderNumber) {
        final var prisonerDetails = prisonApiService.getPrisonerDetails(offenderNumber);

        if (prisonerDetails.recall() || prisonerDetails.legalStatus().equals("RECALL")) {
            return Reason.RECALL;
        }

        return Reason.UNKNOWN;
    }
}
