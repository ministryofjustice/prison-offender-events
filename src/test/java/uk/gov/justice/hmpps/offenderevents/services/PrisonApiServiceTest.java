package uk.gov.justice.hmpps.offenderevents.services;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.justice.hmpps.offenderevents.services.wiremock.HMPPSAuthExtension;
import uk.gov.justice.hmpps.offenderevents.services.wiremock.PrisonApiExtension;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith({PrisonApiExtension.class, HMPPSAuthExtension.class})
@ActiveProfiles(profiles = "test")
@SpringBootTest
class PrisonApiServiceTest {
    @Autowired
    private PrisonApiService service;
    @MockBean
    private HMPPSDomainEventsEmitter hmppsDomainEventsEmitter;
    @MockBean
    private PrisonEventsEmitter prisonEventsEmitter;

    @Nested
    class GetPrisonerDetails {
        @BeforeEach
        void setUp() {
            PrisonApiExtension.server.stubPrisonerDetails("A7841DY", "REMAND", false);
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

            assertThat(prisonerDetails.legalStatus()).isEqualTo("REMAND");
        }

        @Test
        @DisplayName("can parse the recall status")
        void canParseTheRecallStatus() {
            final var prisonerDetails = service.getPrisonerDetails("A7841DY");

            assertThat(prisonerDetails.recall()).isFalse();
        }
    }
}
