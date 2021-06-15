package uk.gov.justice.hmpps.offenderevents.services;

import org.springframework.stereotype.Component;

@Component
public class ReleasePrisonerReasonCalculator {

    public ReleaseReason calculateReasonForRelease(String offenderNumber) {
        return new ReleaseReason(Reason.UNKNOWN);
    }

    enum Reason {
        UNKNOWN,
        TEMPORARY_ABSENCE
    }

    record ReleaseReason(Reason reason) {
    }
}
