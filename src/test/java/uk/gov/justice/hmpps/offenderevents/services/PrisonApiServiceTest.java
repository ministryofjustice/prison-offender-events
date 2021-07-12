package uk.gov.justice.hmpps.offenderevents.services;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.justice.hmpps.offenderevents.config.OffenderEventsProperties;
import uk.gov.justice.hmpps.offenderevents.config.WebClientConfiguration;
import uk.gov.justice.hmpps.offenderevents.services.wiremock.HMPPSAuthExtension;
import uk.gov.justice.hmpps.offenderevents.services.wiremock.PrisonApiExtension;
import uk.gov.justice.hmpps.offenderevents.services.wiremock.PrisonApiMockServer.MovementFragment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith({PrisonApiExtension.class, HMPPSAuthExtension.class})
@ActiveProfiles(profiles = "test")
@SpringBootTest(classes = {PrisonApiService.class, WebClientConfiguration.class, OffenderEventsProperties.class, SecurityAutoConfiguration.class, OAuth2ClientAutoConfiguration.class})
class PrisonApiServiceTest {
    @Autowired
    private PrisonApiService service;

    @Nested
    class GetPrisonerDetails {
        @BeforeEach
        void setUp() {
            PrisonApiExtension.server.stubPrisonerDetails("A7841DY", "REMAND", false, "ADM", "HP", "INACTIVE OUT", "MDI");
        }

        @Test
        @DisplayName("Will request prisoner details for offender number")
        void willRequestPrisonerDetailsForOffenderNumber() {
            service.getPrisonerDetails("A7841DY");

            PrisonApiExtension.server.verify(
                WireMock.getRequestedFor(WireMock.urlEqualTo("/api/offenders/A7841DY"))
                    .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
            );
        }

        @Test
        @DisplayName("can parse the legal status")
        void canParseTheLegalStatus() {
            final var prisonerDetails = service.getPrisonerDetails("A7841DY");

            assertThat(prisonerDetails.legalStatus()).isEqualTo(LegalStatus.REMAND);
        }

        @Test
        @DisplayName("can parse the recall status")
        void canParseTheRecallStatus() {
            final var prisonerDetails = service.getPrisonerDetails("A7841DY");

            assertThat(prisonerDetails.recall()).isFalse();
        }

        @Test
        @DisplayName("can parse last movement code")
        void canParseLastMovementCode() {
            final var prisonerDetails = service.getPrisonerDetails("A7841DY");

            assertThat(prisonerDetails.lastMovementTypeCode()).isEqualTo("ADM");
        }

        @Test
        @DisplayName("can parse last locationId")
        void canParselatestLocationId() {
            final var prisonerDetails = service.getPrisonerDetails("A7841DY");

            assertThat(prisonerDetails.latestLocationId()).isEqualTo("MDI");
        }

        @Test
        @DisplayName("can parse last movement reason code")
        void canParseLastMovementReasonCode() {
            final var prisonerDetails = service.getPrisonerDetails("A7841DY");

            assertThat(prisonerDetails.lastMovementReasonCode()).isEqualTo("HP");
        }

        @Test
        @DisplayName("can calculate current location")
        void canCalculateCurrentLocation() {
            PrisonApiExtension.server.stubPrisonerDetails("A7841DY",
                "REMAND",
                false,
                "ADM",
                "HP",
                "INACTIVE OUT",
                "MDI");
            assertThat(service.getPrisonerDetails("A7841DY").currentLocation())
                .isEqualTo(CurrentLocation.OUTSIDE_PRISON);

            PrisonApiExtension.server.stubPrisonerDetails("A7841DY",
                "REMAND",
                false,
                "ADM",
                "HP",
                "ACTIVE IN",
                "MDI");
            assertThat(service.getPrisonerDetails("A7841DY").currentLocation()).isEqualTo(CurrentLocation.IN_PRISON);

            PrisonApiExtension.server.stubPrisonerDetails("A7841DY",
                "REMAND",
                false,
                "ADM",
                "HP",
                "BACON",
                "MDI");
            assertThat(service.getPrisonerDetails("A7841DY").currentLocation()).isNull();

            PrisonApiExtension.server.stubPrisonerDetails("A7841DY",
                "REMAND",
                false,
                "ADM",
                "HP",
                "INACTIVE TRN",
                "MDI");
            assertThat(service.getPrisonerDetails("A7841DY").currentLocation()).isEqualTo(CurrentLocation.BEING_TRANSFERRED);
        }

        @Test
        @DisplayName("can calculate current status")
        void canCalculateCurrentStatus() {
            PrisonApiExtension.server.stubPrisonerDetails("A7841DY",
                "REMAND",
                false,
                "ADM",
                "HP",
                "INACTIVE OUT",
                "MDI");
            assertThat(service.getPrisonerDetails("A7841DY").currentPrisonStatus())
                .isEqualTo(CurrentPrisonStatus.NOT_UNDER_PRISON_CARE);

            PrisonApiExtension.server.stubPrisonerDetails("A7841DY",
                "REMAND",
                false,
                "ADM",
                "HP",
                "ACTIVE IN",
                "MDI");
            assertThat(service.getPrisonerDetails("A7841DY").currentPrisonStatus())
                .isEqualTo(CurrentPrisonStatus.UNDER_PRISON_CARE);

            PrisonApiExtension.server.stubPrisonerDetails("A7841DY",
                "REMAND",
                false,
                "ADM",
                "HP",
                "BACON",
                "MDI");
            assertThat(service.getPrisonerDetails("A7841DY").currentPrisonStatus()).isNull();
        }
    }

    @Nested
    class GetMovements {
        @BeforeEach
        void setUp() {
            PrisonApiExtension.server.stubMovements("A7841DY", List.of(
                new MovementFragment("IN", LocalDateTime.parse("2020-07-19T10:00:40")),
                new MovementFragment("OUT", LocalDateTime.parse("2020-07-20T11:00:40"))));
        }

        @Test
        @DisplayName("can retrieve all movements")
        void canRetrieveAllMovements() {
          final var movements = service.getMovements("A7841DY");

          assertThat(movements).hasSize(2);
        }
        @Test
        @DisplayName("can parse the direction code")
        void canParseTheDirectionCode() {
            final var movements = service.getMovements("A7841DY");

            assertThat(movements.get(0).directionCode()).isEqualTo("IN");
            assertThat(movements.get(1).directionCode()).isEqualTo("OUT");
        }

        @Test
        @DisplayName("can parse movement date")
        void canParseMovementDate() {
            final var movements = service.getMovements("A7841DY");
            assertThat(movements.get(0).movementDate()).isEqualTo(LocalDate.parse("2020-07-19"));
            assertThat(movements.get(1).movementDate()).isEqualTo(LocalDate.parse("2020-07-20"));
        }
    }
}
