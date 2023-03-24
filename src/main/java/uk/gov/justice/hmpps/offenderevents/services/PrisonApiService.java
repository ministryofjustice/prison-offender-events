package uk.gov.justice.hmpps.offenderevents.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;

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
    REMAND,
    OTHER
}

@Service
public class PrisonApiService {

    private final WebClient webClient;
    private final Duration timeout;

    public PrisonApiService(final WebClient prisonApiWebClient, @Value("${api.prisoner-timeout:30s}") final Duration timeout) {
        this.webClient = prisonApiWebClient;
        this.timeout = timeout;
    }

    public PrisonerDetails getPrisonerDetails(final String offenderNumber) {
        return webClient.get()
            .uri(format("/api/offenders/%s", offenderNumber))
            .retrieve()
            .bodyToMono(PrisonerDetails.class)
            .block(timeout);
    }

    public Optional<String> getPrisonerNumberForBookingId(final Long bookingId) {
        final var basicBookingDetail = webClient.get()
            .uri(format("/api/bookings/%d?basicInfo=true&extraInfo=false", bookingId))
            .retrieve()
            .bodyToMono(BasicBookingDetail.class)
            .block(timeout);
        return basicBookingDetail != null ? Optional.of(basicBookingDetail.offenderNo()) : Optional.empty();
    }

    public List<Movement> getMovements(final String offenderNumber) {
        return webClient.post()
            .uri("/api/movements/offenders?latestOnly=false&allBookings=true")
            .bodyValue(List.of(offenderNumber))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<Movement>>() {
            })
            .block(timeout);
    }

    public List<BookingIdentifier> getIdentifiersByBookingId(final Long bookingId) {
        return webClient.get()
            .uri(format("/api/bookings/%d/identifiers?type=MERGED", bookingId))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<BookingIdentifier>>() {
            })
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

    public static final String UNCONVICTED_REMAND = "N";
    public static final String LICENCE_REVOKED = "L";
    public static final String RECALL_FROM_DETENTION_TRAINING_ORDER = "Y";
    public static final String RECALL_FROM_HDC = "B";
    public static final String TRANSFER_IN = "INT";
    public static final String TRANSFER_IN_VIA_COURT = "TRNCRT";
    public static final String TRANSFER_IN_VIA_TAP = "TRNTAP";

    public LegalStatus legalStatus() {
        return legalStatus == null ? LegalStatus.UNKNOWN : legalStatus;
    }

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
            case TRANSFER_IN, TRANSFER_IN_VIA_COURT, TRANSFER_IN_VIA_TAP -> MovementReason.TRANSFER;
            case LICENCE_REVOKED, RECALL_FROM_HDC, RECALL_FROM_DETENTION_TRAINING_ORDER -> MovementReason.RECALL;
            case UNCONVICTED_REMAND -> MovementReason.REMAND;
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

record Movement(String directionCode, LocalDate movementDate) {
}

record BookingIdentifier(String value) {
}

record BasicBookingDetail(String offenderNo) {
}