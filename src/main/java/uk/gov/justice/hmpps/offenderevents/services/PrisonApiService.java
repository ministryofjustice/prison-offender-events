package uk.gov.justice.hmpps.offenderevents.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Optional;

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

enum CurrentLocation {
    IN_PRISON,
    OUTSIDE_PRISON,
    BEING_TRANSFERRED,
}

enum CurrentPrisonStatus {
    UNDER_PRISON_CARE,
    NOT_UNDER_PRISON_CARE
}

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

record PrisonerDetails(LegalStatus legalStatus,
                       boolean recall,
                       String lastMovementTypeCode,
                       String lastMovementReasonCode,
                       String status,
                       String statusReason,
                       String latestLocationId
) {
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

    public CurrentLocation currentLocation() {
        return Optional.ofNullable(status).map(this::secondOf).map(location -> switch (location) {
            case "IN" -> CurrentLocation.IN_PRISON;
            case "OUT" -> CurrentLocation.OUTSIDE_PRISON;
            case "TRN" -> CurrentLocation.BEING_TRANSFERRED;
            default -> null;
        }).orElse(null);
    }

    public CurrentPrisonStatus currentPrisonStatus() {
        return Optional.ofNullable(status).map(this::firstOf).map(location -> switch (location) {
            case "ACTIVE" -> CurrentPrisonStatus.UNDER_PRISON_CARE;
            case "INACTIVE" -> CurrentPrisonStatus.NOT_UNDER_PRISON_CARE;
            default -> null;
        }).orElse(null);
    }

    private String firstOf(String value) {
        return elementOf(value, 0);
    }

    private String secondOf(String value) {
        return elementOf(value, 1);
    }

    private String elementOf(String value, int index) {
        var elements = value.split(" ");
        if (elements.length > index) {
            return elements[index];
        }
        return null;
    }
}
