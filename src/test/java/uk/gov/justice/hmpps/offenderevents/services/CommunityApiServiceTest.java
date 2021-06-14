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
import uk.gov.justice.hmpps.offenderevents.services.wiremock.CommunityApiExtension;
import uk.gov.justice.hmpps.offenderevents.services.wiremock.HMPPSAuthExtension;

import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith({CommunityApiExtension.class, HMPPSAuthExtension.class})
@ActiveProfiles(profiles = "test")
@SpringBootTest(classes = {CommunityApiService.class, WebClientConfiguration.class, OffenderEventsProperties.class, SecurityAutoConfiguration.class, OAuth2ClientAutoConfiguration.class})
class CommunityApiServiceTest {
    @Autowired
    private CommunityApiService service;

    @Nested
    class GetRecalls {
        @Nested
        class WhenOffenderNotFound {
            @BeforeEach
            void setUp() {
                CommunityApiExtension.server.stubForRecallNotFound("A7841DY");
            }

            @Test
            @DisplayName("will return empty when")
            void willReturnEmptyWhen() {
              assertThat(service.getRecalls("A7841DY")).isEmpty();
            }
        }
        @Nested
        class WhenOffenderFound {
            @BeforeEach
            void setUp() {
                CommunityApiExtension.server.stubForRecall("A7841DY");
            }

            @Test
            @DisplayName("Will request recalls for offender number")
            void willRequestPrisonerDetailsForOffenderNumber() {
                service.getRecalls("A7841DY");

                CommunityApiExtension.server.verify(
                    WireMock
                        .getRequestedFor(WireMock.urlEqualTo("/secure/offenders/nomsNumber/A7841DY/convictions/active/nsis/recall"))
                        .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
                );
            }

            @Test
            @DisplayName("can parse the recalls")
            void canParseRecalls() {
                final var recalls = service.getRecalls("A7841DY").orElseThrow();

                assertThat(recalls).hasSize(2);
            }

            @Test
            @DisplayName("can parse the referralDate")
            void canParseReferralDate() {
                final var recalls = service.getRecalls("A7841DY").orElseThrow();

                assertThat(recalls.get(0).referralDate()).isEqualTo("2021-05-12");
                assertThat(recalls.get(1).referralDate()).isEqualTo("2021-05-13");
            }

            @Test
            @DisplayName("can parse the recallRejectedOrWithdrawn")
            void canParseRecallRejectedOrWithdrawn() {
                final var recalls = service.getRecalls("A7841DY").orElseThrow();

                assertThat(recalls.get(0).recallRejectedOrWithdrawn()).isTrue();
                assertThat(recalls.get(1).recallRejectedOrWithdrawn()).isFalse();
            }

            @Test
            @DisplayName("can parse the outcomeRecall")
            void canParseOutcomeRecall() {
                final var recalls = service.getRecalls("A7841DY").orElseThrow();

                assertThat(recalls.get(1).outcomeRecall()).isNull();
                assertThat(recalls.get(0).outcomeRecall()).isFalse();
            }
        }
    }
}
