package uk.gov.justice.hmpps.offenderevents.services

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.hmpps.offenderevents.config.OffenderEventsProperties
import uk.gov.justice.hmpps.offenderevents.config.WebClientConfiguration
import uk.gov.justice.hmpps.offenderevents.services.CurrentLocation.BEING_TRANSFERRED
import uk.gov.justice.hmpps.offenderevents.services.CurrentLocation.IN_PRISON
import uk.gov.justice.hmpps.offenderevents.services.CurrentLocation.OUTSIDE_PRISON
import uk.gov.justice.hmpps.offenderevents.services.CurrentPrisonStatus.NOT_UNDER_PRISON_CARE
import uk.gov.justice.hmpps.offenderevents.services.CurrentPrisonStatus.UNDER_PRISON_CARE
import uk.gov.justice.hmpps.offenderevents.services.LegalStatus.REMAND
import uk.gov.justice.hmpps.offenderevents.services.wiremock.HMPPSAuthExtension
import uk.gov.justice.hmpps.offenderevents.services.wiremock.PrisonApiExtension
import uk.gov.justice.hmpps.offenderevents.services.wiremock.PrisonApiExtension.server
import uk.gov.justice.hmpps.offenderevents.services.wiremock.PrisonApiMockServer.MovementFragment
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(PrisonApiExtension::class, HMPPSAuthExtension::class)
@ActiveProfiles(profiles = ["test"])
@SpringBootTest(classes = [PrisonApiService::class, WebClientConfiguration::class, OffenderEventsProperties::class, SecurityAutoConfiguration::class, OAuth2ClientAutoConfiguration::class, WebClientAutoConfiguration::class])
internal class PrisonApiServiceTest {
  @Autowired
  private lateinit var service: PrisonApiService

  @Nested
  internal inner class GetPrisonerDetails {
    @BeforeEach
    fun setUp() {
      server.stubPrisonerDetails(
        "A7841DY",
        "REMAND",
        false,
        "ADM",
        "HP",
        "INACTIVE OUT",
        "MDI",
      )
    }

    @Test
    @DisplayName("Will request prisoner details for offender number")
    fun willRequestPrisonerDetailsForOffenderNumber() {
      service.getPrisonerDetails("A7841DY")
      server.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/api/offenders/A7841DY"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    @DisplayName("can parse the legal status")
    fun canParseTheLegalStatus() {
      val prisonerDetails = service.getPrisonerDetails("A7841DY")
      assertThat(prisonerDetails.legalStatus).isEqualTo(REMAND)
    }

    @Test
    @DisplayName("can parse the recall status")
    fun canParseTheRecallStatus() {
      val prisonerDetails = service.getPrisonerDetails("A7841DY")
      assertThat(prisonerDetails.recall).isFalse
    }

    @Test
    @DisplayName("can parse last movement code")
    fun canParseLastMovementCode() {
      val prisonerDetails = service.getPrisonerDetails("A7841DY")
      assertThat(prisonerDetails.lastMovementTypeCode).isEqualTo("ADM")
    }

    @Test
    @DisplayName("can parse last locationId")
    fun canParselatestLocationId() {
      val prisonerDetails = service.getPrisonerDetails("A7841DY")
      assertThat(prisonerDetails.latestLocationId).isEqualTo("MDI")
    }

    @Test
    @DisplayName("can parse last movement reason code")
    fun canParseLastMovementReasonCode() {
      val prisonerDetails = service.getPrisonerDetails("A7841DY")
      assertThat(prisonerDetails.lastMovementReasonCode).isEqualTo("HP")
    }

    @Test
    @DisplayName("can calculate current location")
    fun canCalculateCurrentLocation() {
      server.stubPrisonerDetails(
        "A7841DY",
        "REMAND",
        false,
        "ADM",
        "HP",
        "INACTIVE OUT",
        "MDI",
      )
      assertThat(service.getPrisonerDetails("A7841DY").currentLocation()).isEqualTo(OUTSIDE_PRISON)
      server.stubPrisonerDetails(
        "A7841DY",
        "REMAND",
        false,
        "ADM",
        "HP",
        "ACTIVE IN",
        "MDI",
      )
      assertThat(service.getPrisonerDetails("A7841DY").currentLocation()).isEqualTo(IN_PRISON)
      server.stubPrisonerDetails(
        "A7841DY",
        "REMAND",
        false,
        "ADM",
        "HP",
        "BACON",
        "MDI",
      )
      assertThat(service.getPrisonerDetails("A7841DY").currentLocation()).isNull()
      server.stubPrisonerDetails(
        "A7841DY",
        "REMAND",
        false,
        "ADM",
        "HP",
        "INACTIVE TRN",
        "MDI",
      )
      assertThat(service.getPrisonerDetails("A7841DY").currentLocation()).isEqualTo(BEING_TRANSFERRED)
    }

    @Test
    @DisplayName("can calculate current status")
    fun canCalculateCurrentStatus() {
      server.stubPrisonerDetails(
        "A7841DY",
        "REMAND",
        false,
        "ADM",
        "HP",
        "INACTIVE OUT",
        "MDI",
      )
      assertThat(service.getPrisonerDetails("A7841DY").currentPrisonStatus()).isEqualTo(NOT_UNDER_PRISON_CARE)
      server.stubPrisonerDetails(
        "A7841DY",
        "REMAND",
        false,
        "ADM",
        "HP",
        "ACTIVE IN",
        "MDI",
      )
      assertThat(service.getPrisonerDetails("A7841DY").currentPrisonStatus()).isEqualTo(UNDER_PRISON_CARE)
      server.stubPrisonerDetails(
        "A7841DY",
        "REMAND",
        false,
        "ADM",
        "HP",
        "BACON",
        "MDI",
      )
      assertThat(service.getPrisonerDetails("A7841DY").currentPrisonStatus()).isNull()
    }
  }

  @Nested
  internal inner class GetBasicPrisonerDetails {
    @BeforeEach
    fun setUp() {
      server.stubBasicPrisonerDetails(
        "A7841DY",
        1200835L,
      )
    }

    @Test
    @DisplayName("Will request basic prisoner details for booking number")
    fun willRequestPrisonerDetailsForOffenderNumber() {
      service.getPrisonerNumberForBookingId(1200835L)
      server.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/api/bookings/1200835?basicInfo=true&extraInfo=false"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    @DisplayName("can parse the offender Number")
    fun canParseTheOffenderNo() {
      val offenderNo = service.getPrisonerNumberForBookingId(1200835L)
      assertThat(offenderNo).isPresent
      assertThat(offenderNo.get()).isEqualTo("A7841DY")
    }
  }

  @Nested
  internal inner class GetMergeIdentifiers {
    @BeforeEach
    fun setUp() {
      server.stubPrisonerIdentifiers(
        "A5841DY",
        1200835L,
      )
    }

    @Test
    @DisplayName("Will request prisoner merge identifiers for booking number")
    fun willRequestMergeIdentifiersForBookingNumber() {
      service.getIdentifiersByBookingId(1200835L)
      server.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/api/bookings/1200835/identifiers?type=MERGED"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    @DisplayName("can parse the merged offender Number")
    fun canParseTheOffenderNo() {
      val identifiers = service.getIdentifiersByBookingId(1200835L)
      assertThat(identifiers).isNotEmpty
      assertThat(identifiers).hasSize(1)
      assertThat(identifiers.get(0).value).isEqualTo("A5841DY")
    }
  }

  @Nested
  internal inner class GetMovements {
    @BeforeEach
    fun setUp() {
      server.stubMovements(
        "A7841DY",
        listOf(
          MovementFragment("IN", LocalDateTime.parse("2020-07-19T10:00:40")),
          MovementFragment("OUT", LocalDateTime.parse("2020-07-20T11:00:40")),
        ),
      )
    }

    @Test
    @DisplayName("can retrieve all movements")
    fun canRetrieveAllMovements() {
      val movements = service.getMovements("A7841DY")
      assertThat(movements).hasSize(2)
    }

    @Test
    @DisplayName("can parse the direction code")
    fun canParseTheDirectionCode() {
      val movements = service.getMovements("A7841DY")
      assertThat(movements[0].directionCode).isEqualTo("IN")
      assertThat(movements[1].directionCode).isEqualTo("OUT")
    }

    @Test
    @DisplayName("can parse movement date")
    fun canParseMovementDate() {
      val movements = service.getMovements("A7841DY")
      assertThat(movements[0].movementDate).isEqualTo(LocalDate.parse("2020-07-19"))
      assertThat(movements[1].movementDate).isEqualTo(LocalDate.parse("2020-07-20"))
    }
  }
}
