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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

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
            PrisonApiExtension.server.stubPrisonerDetails("A7841DY", "REMAND", false, "ADM");
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

    }
}
