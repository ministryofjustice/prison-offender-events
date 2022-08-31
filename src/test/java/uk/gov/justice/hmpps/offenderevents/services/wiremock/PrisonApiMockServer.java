package uk.gov.justice.hmpps.offenderevents.services.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.lang.String.format;

public class PrisonApiMockServer extends WireMockServer {
    PrisonApiMockServer() {
        super(8086);
    }

    public void stubPrisonerDetails(String offenderNumber, String legalStatus, boolean recall, String lastMovementTypeCode,
                                    String lastMovementReasonCode, String status, String latestLocationId) {
        stubFor(
            get(String.format("/api/offenders/%s", offenderNumber)).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(prisonerDetails(offenderNumber, legalStatus, recall, lastMovementTypeCode, lastMovementReasonCode, status, latestLocationId))
                    .withStatus(200)
            )
        );
    }

    public void stubBasicPrisonerDetails(String offenderNumber, Long bookingId) {
        stubFor(
            get(String.format("/api/bookings/%d?basicInfo=true&extraInfo=false", bookingId)).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(basicPrisonerDetails(offenderNumber, bookingId))
                    .withStatus(200)
            )
        );
    }

    public void stubPrisonerIdentifiers(String mergedNumber, Long bookingId) {
        stubFor(
            get(String.format("/api/bookings/%d/identifiers?type=MERGED", bookingId)).willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(mergeIdentifier(mergedNumber))
                    .withStatus(200)
            )
        );
    }

    public void stubMovements(String offenderNumber, List<MovementFragment> movements) {
        Function<MovementFragment, String> asJson = (movementFragment) -> {
            final var createDateTime = movementFragment.movementDateTime().format(DateTimeFormatter.ISO_DATE_TIME);
            final var movementDate = movementFragment
                .movementDateTime()
                .toLocalDate()
                .format(DateTimeFormatter.ISO_DATE);
            final var movementTime = movementFragment
                .movementDateTime()
                .toLocalTime()
                .format(DateTimeFormatter.ISO_LOCAL_TIME);
            final var directionCode = movementFragment.directionCode();
            return String.format("""
                    {
                        "offenderNo": "%s",
                        "createDateTime": "%s",
                        "fromAgency": "WWI",
                        "fromAgencyDescription": "Wandsworth (HMP)",
                        "toAgency": "FBI",
                        "toAgencyDescription": "Forest Bank (HMP & YOI)",
                        "fromCity": "",
                        "toCity": "",
                        "movementType": "%s",
                        "movementTypeDescription": "some description",
                        "directionCode": "%s",
                        "movementDate": "%s",
                        "movementTime": "%s",
                        "movementReason": "some reason"
                    }
                """, offenderNumber, createDateTime, directionCode.equals("IN") ? "ADM" : "REL", directionCode, movementDate, movementTime);
        };


        stubFor(
            post("/api/movements/offenders?latestOnly=false&allBookings=true").willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(String.format("""
                        [
                            %s
                        ]
                        """, movements.stream().map(asJson).collect(Collectors.joining(","))))
                    .withStatus(200)
            )
        );
    }

    public void stubFirstPollWithOffenderEvents(String events) {
        final var scenarioName = UUID.randomUUID().toString();
        stubFor(
            get(WireMock.urlPathEqualTo("/api/events"))
                .inScenario(scenarioName)
                .whenScenarioStateIs(STARTED)
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(events)
                        .withStatus(200)
                )
                .willSetStateTo("Subsequent poll")
        );
        stubFor(
            get(WireMock.urlPathEqualTo("/api/events"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("Subsequent poll")
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")
                        .withStatus(200)
                )
        );
    }

    private String prisonerDetails(String offenderNumber, String legalStatus, boolean recall, String lastMovementTypeCode,
                                   String lastMovementReasonCode, String status, String latestLocationId) {
        return String.format("""
            {
                "offenderNo": "%s",
                "bookingId": 1201233,
                "bookingNo": "38559A",
                "offenderId": 2582162,
                "rootOffenderId": 2582162,
                "firstName": "ANDY",
                "lastName": "REMAND",
                "dateOfBirth": "1965-07-19",
                "age": 55,
                "activeFlag": true,
                "agencyId": "MDI",
                "assignedLivingUnitId": 4012,
                "alertsCodes": [],
                "activeAlertCount": 0,
                "inactiveAlertCount": 0,
                "alerts": [],
                "assignedLivingUnit": {
                    "agencyId": "MDI",
                    "locationId": 4012,
                    "description": "RECP",
                    "agencyName": "Moorland (HMP & YOI)"
                },
                "physicalAttributes": {
                    "sexCode": "M",
                    "gender": "Male",
                    "raceCode": "M2",
                    "ethnicity": "Mixed: White and Black African"
                },
                "physicalCharacteristics": [],
                "profileInformation": [
                    {
                        "type": "YOUTH",
                        "question": "Youth Offender?",
                        "resultValue": "No"
                    }
                ],
                "physicalMarks": [],
                "inOutStatus": "IN",
                "identifiers": [],
                "personalCareNeeds": [],
                "sentenceDetail": {
                    "bookingId": 1201233
                },
                "offenceHistory": [],
                "sentenceTerms": [],
                "aliases": [],
                "status": "%s",
                "statusReason": "ADM-N",
                "lastMovementTypeCode": "%s",
                "lastMovementReasonCode": "%s",
                "legalStatus": "%s",
                "recall": %b,
                "imprisonmentStatus": "TRL",
                "imprisonmentStatusDescription": "Committed to Crown Court for Trial",
                "privilegeSummary": {
                    "bookingId": 1201233,
                    "iepLevel": "Standard",
                    "iepDate": "2021-06-01",
                    "iepTime": "2021-06-01T10:04:36",
                    "daysSinceReview": 0,
                    "iepDetails": []
                },
                "receptionDate": "2021-06-01",
                "locationDescription": "Moorland (HMP & YOI)",
                "latestLocationId": "%s"
            }
                        """, offenderNumber, status, lastMovementTypeCode, lastMovementReasonCode, legalStatus, recall, latestLocationId);
    }

    private String basicPrisonerDetails(String offenderNumber, Long bookingId) {
        return String.format("""
            {
                "offenderNo": "%s",
                "bookingId": %d
            }
            """, offenderNumber, bookingId);
    }

    private String mergeIdentifier(String mergedNumber) {
        return String.format("""
            [
                {
                    "type": "MERGE",
                    "value": "%s"
                }
            ]
            """, mergedNumber);
    }

    public void stubHealthPing(Integer status) {
        final var up = """
            {"status": "up"}""";
        final var down = """
            {"status": "down"}""";
        stubFor(
            WireMock.get("/health/ping").willReturn(
                WireMock.aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody((status == 200) ? up : down)
                    .withStatus(status)));
    }
    public record MovementFragment(String directionCode, LocalDateTime movementDateTime) {
    }
}
