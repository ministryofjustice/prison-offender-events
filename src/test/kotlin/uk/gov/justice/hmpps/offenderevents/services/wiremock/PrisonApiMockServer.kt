package uk.gov.justice.hmpps.offenderevents.services.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Function
import java.util.stream.Collectors

class PrisonApiMockServer internal constructor() : WireMockServer(8086) {
  fun stubPrisonerDetails(
    offenderNumber: String?,
    legalStatus: String,
    recall: Boolean,
    lastMovementTypeCode: String,
    lastMovementReasonCode: String,
    status: String,
    latestLocationId: String,
  ) {
    stubFor(
      WireMock.get(String.format("/api/offenders/%s", offenderNumber)).willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            prisonerDetails(
              offenderNumber,
              legalStatus,
              recall,
              lastMovementTypeCode,
              lastMovementReasonCode,
              status,
              latestLocationId,
            ),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubPrisonerDetails404(offenderNumber: String?) {
    stubFor(
      WireMock.get(String.format("/api/offenders/%s", offenderNumber)).willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(404),
      ),
    )
  }

  fun verifyPrisonerDetails404(offenderNumber: String?) {
    verify(WireMock.getRequestedFor(WireMock.urlEqualTo(String.format("/api/offenders/%s", offenderNumber))))
  }

  fun stubBasicPrisonerDetails(offenderNumber: String, bookingId: Long?) {
    stubFor(
      WireMock.get(String.format("/api/bookings/%d?basicInfo=true&extraInfo=false", bookingId)).willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(basicPrisonerDetails(offenderNumber, bookingId))
          .withStatus(200),
      ),
    )
  }

  fun stubPrisonerIdentifiers(mergedNumber: String, bookingId: Long?) {
    stubFor(
      WireMock.get(String.format("/api/bookings/%d/identifiers?type=MERGED", bookingId)).willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(mergeIdentifier(mergedNumber))
          .withStatus(200),
      ),
    )
  }

  fun stubMovements(offenderNumber: String?, movements: List<MovementFragment>) {
    val asJson = Function { (directionCode, movementDateTime): MovementFragment ->
      val createDateTime = movementDateTime.format(
        DateTimeFormatter.ISO_DATE_TIME,
      )
      val movementDate = movementDateTime
        .toLocalDate()
        .format(DateTimeFormatter.ISO_DATE)
      val movementTime = movementDateTime
        .toLocalTime()
        .format(DateTimeFormatter.ISO_LOCAL_TIME)
      String.format(
        """
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
                
        """.trimIndent(),
        offenderNumber,
        createDateTime,
        if (directionCode == "IN") "ADM" else "REL",
        directionCode,
        movementDate,
        movementTime,
      )
    }
    stubFor(
      WireMock.post("/api/movements/offenders?latestOnly=false&allBookings=true").willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            String.format(
              """
                        [
                            %s
                        ]
                        
              """.trimIndent(),
              movements.stream().map(asJson).collect(Collectors.joining(",")),
            ),
          )
          .withStatus(200),
      ),
    )
  }

  private fun prisonerDetails(
    offenderNumber: String?,
    legalStatus: String,
    recall: Boolean,
    lastMovementTypeCode: String,
    lastMovementReasonCode: String,
    status: String,
    latestLocationId: String,
  ): String {
    return String.format(
      """
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
                        
      """.trimIndent(),
      offenderNumber,
      status,
      lastMovementTypeCode,
      lastMovementReasonCode,
      legalStatus,
      recall,
      latestLocationId,
    )
  }

  private fun basicPrisonerDetails(offenderNumber: String, bookingId: Long?): String {
    return String.format(
      """
            {
                "offenderNo": "%s",
                "bookingId": %d
            }
            
      """.trimIndent(),
      offenderNumber,
      bookingId,
    )
  }

  private fun mergeIdentifier(mergedNumber: String): String {
    return String.format(
      """
            [
                {
                    "type": "MERGE",
                    "value": "%s"
                }
            ]
            
      """.trimIndent(),
      mergedNumber,
    )
  }

  fun stubHealthPing(status: Int) {
    val up = """
            {"status": "up"}
    """.trimIndent()
    val down = """
            {"status": "down"}
    """.trimIndent()
    stubFor(
      WireMock.get("/health/ping").willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) up else down)
          .withStatus(status),
      ),
    )
  }

  @JvmRecord
  data class MovementFragment(val directionCode: String, val movementDateTime: LocalDateTime)
}
