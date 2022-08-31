package uk.gov.justice.hmpps.offenderevents.services;

import com.microsoft.applicationinsights.TelemetryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Slf4j
public class MergeRecordDiscriminator {
    private final PrisonApiService prisonApiService;

    private final TelemetryClient telemetryClient;

    public MergeRecordDiscriminator(PrisonApiService prisonApiService,
                                    TelemetryClient telemetryClient) {
        this.prisonApiService = prisonApiService;
        this.telemetryClient = telemetryClient;
    }

    public List<MergeOutcome> identifyMergedPrisoner(Long bookingId) {

        return prisonApiService.getPrisonerNumberForBookingId(bookingId)
            .map(prisonerNumber -> {
                log.debug("Check for merges for booking ID {}, prisoner Number {}", bookingId, prisonerNumber);
                final var mergedOffenders = prisonApiService.getIdentifiersByBookingId(bookingId);

                return mergedOffenders.stream()
                    .peek(mergedNumber -> {
                        final var trackingAttributes = Map.of(
                            "bookingId", bookingId.toString(),
                            "mergedFrom", mergedNumber.value(),
                            "mergedTo", prisonerNumber
                        );
                        telemetryClient.trackEvent("POEMergeEvent", trackingAttributes, null);
                        log.debug("Prisoner record merged {} --> {}", mergedNumber.value(), prisonerNumber);
                    })
                    .map(mergedNumber -> new MergeOutcome(mergedNumber.value(), prisonerNumber))
                    .collect(Collectors.toList());
            })
            .orElse(Collections.emptyList());
    }


    record MergeOutcome(String mergedNumber, String remainingNumber) {
    }

}
