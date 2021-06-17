package uk.gov.justice.hmpps.offenderevents.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Service
@Slf4j
public class PrisonApiService {

    private final WebClient webClient;
    private final Duration timeout;

    public PrisonApiService(final WebClient prisonApiWebClient, @Value("${api.prisoner-timeout:30s}") final Duration timeout) {
        this.webClient = prisonApiWebClient;
        this.timeout = timeout;
    }

    public PrisonerDetails getPrisonerDetails(final String offenderNumber) {
        return webClient.get()
            .uri(String.format("/api/offenders/%s", offenderNumber))
            .retrieve()
            .bodyToMono(PrisonerDetails.class)
            .block(timeout);
    }

}

record PrisonerDetails(LegalStatus legalStatus, boolean recall, String lastMovementTypeCode, String lastMovementReasonCode, String status, String statusReason) {
    public MovementType typeOfMovement() {
        return switch (lastMovementTypeCode) {
            case "TAP" -> MovementType.TEMPORARY_ABSENCE;
            case "ADM" -> MovementType.ADMISSION;
            case "REL" -> MovementType.RELEASED;
            case "CRT" -> MovementType.COURT;
            case "TRN" -> MovementType.TRANSFER;
            default -> MovementType.OTHER;
        };
    }

    public MovementReason movementReason() {
        return switch (lastMovementReasonCode) {
            case "HP" -> MovementReason.HOSPITALISATION;
            case "INT" -> MovementReason.TRANSFER;
            case "L" -> MovementReason.RECALL;
            default -> MovementReason.OTHER;
        };
    }
}


enum LegalStatus {
    RECALL,
    DEAD,
    INDETERMINATE_SENTENCE,
    SENTENCED,
    CONVICTED_UNSENTENCED,
    CIVIL_PRISONER,
    IMMIGRATION_DETAINEE,
    REMAND,
    UNKNOWN,
    OTHER
}

enum MovementType {
    TEMPORARY_ABSENCE,
    COURT,
    ADMISSION,
    RELEASED,
    TRANSFER,
    OTHER
}

enum MovementReason {
    HOSPITALISATION,
    TRANSFER,
    RECALL,
    OTHER
}
