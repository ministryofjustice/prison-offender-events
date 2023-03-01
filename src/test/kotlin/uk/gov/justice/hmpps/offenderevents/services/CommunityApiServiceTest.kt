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
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.hmpps.offenderevents.config.OffenderEventsProperties
import uk.gov.justice.hmpps.offenderevents.config.WebClientConfiguration
import uk.gov.justice.hmpps.offenderevents.services.wiremock.CommunityApiExtension
import uk.gov.justice.hmpps.offenderevents.services.wiremock.CommunityApiExtension.server
import uk.gov.justice.hmpps.offenderevents.services.wiremock.HMPPSAuthExtension

@ExtendWith(CommunityApiExtension::class, HMPPSAuthExtension::class)
@ActiveProfiles(profiles = ["test"])
@SpringBootTest(classes = [CommunityApiService::class, WebClientConfiguration::class, OffenderEventsProperties::class, SecurityAutoConfiguration::class, OAuth2ClientAutoConfiguration::class])
internal class CommunityApiServiceTest {
  @Autowired
  private lateinit var service: CommunityApiService

  @Nested
  internal inner class GetRecalls {
    @Nested
    internal inner class WhenOffenderNotFound {
      @BeforeEach
      fun setUp() {
        server.stubForRecallNotFound("A7841DY")
      }

      @Test
      @DisplayName("will return empty when")
      fun willReturnEmptyWhen() {
        assertThat(service.getRecalls("A7841DY")).isEmpty
      }
    }

    @Nested
    internal inner class WhenOffenderFound {
      @BeforeEach
      fun setUp() {
        server.stubForRecall("A7841DY")
      }

      @Test
      @DisplayName("Will request recalls for offender number")
      fun willRequestPrisonerDetailsForOffenderNumber() {
        service.getRecalls("A7841DY")
        server.verify(
          WireMock
            .getRequestedFor(WireMock.urlEqualTo("/secure/offenders/nomsNumber/A7841DY/convictions/active/nsis/recall"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      @DisplayName("can parse the recalls")
      fun canParseRecalls() {
        val recalls = service.getRecalls("A7841DY").orElseThrow()
        assertThat(recalls).hasSize(2)
      }

      @Test
      @DisplayName("can parse the referralDate")
      fun canParseReferralDate() {
        val recalls = service.getRecalls("A7841DY").orElseThrow()
        assertThat(recalls[0].referralDate).isEqualTo("2021-05-12")
        assertThat(recalls[1].referralDate).isEqualTo("2021-05-13")
      }

      @Test
      @DisplayName("can parse the recallRejectedOrWithdrawn")
      fun canParseRecallRejectedOrWithdrawn() {
        val recalls = service.getRecalls("A7841DY").orElseThrow()
        assertThat(recalls[0].recallRejectedOrWithdrawn).isTrue
        assertThat(recalls[1].recallRejectedOrWithdrawn).isFalse
      }

      @Test
      @DisplayName("can parse the outcomeRecall")
      fun canParseOutcomeRecall() {
        val recalls = service.getRecalls("A7841DY").orElseThrow()
        assertThat(recalls[0].outcomeRecall).isFalse
        assertThat(recalls[1].outcomeRecall == null)
      }
    }
  }
}
