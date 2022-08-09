package uk.gov.justice.hmpps.offenderevents.services

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
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
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent
import uk.gov.justice.hmpps.offenderevents.services.wiremock.HMPPSAuthExtension
import uk.gov.justice.hmpps.offenderevents.services.wiremock.PrisonApiExtension
import uk.gov.justice.hmpps.offenderevents.services.wiremock.PrisonApiExtension.server
import java.time.LocalDateTime

@ExtendWith(PrisonApiExtension::class, HMPPSAuthExtension::class)
@ActiveProfiles(profiles = ["test"])
@SpringBootTest(classes = [ExternalApiService::class, WebClientConfiguration::class, SecurityAutoConfiguration::class, OffenderEventsProperties::class, OAuth2ClientAutoConfiguration::class])
internal class ExternalApiIntegrationTest {
  @Autowired
  private lateinit var service: ExternalApiService

  @Nested
  internal inner class getEvents {

    @Test
    @DisplayName("Makes correct network call")
    fun makesCorrectNetworkCall() {
      server.stubFirstPollWithOffenderEvents("[]")

      service.getEvents(LocalDateTime.of(2020, 1, 1, 2, 30, 0), LocalDateTime.of(2020, 1, 1, 3, 30, 0))

      server.verify(
        getRequestedFor(urlEqualTo("/api/events?sortBy=TIMESTAMP_ASC&from=2020-01-01T02:30&to=2020-01-01T03:30"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    @DisplayName("Parses empty array")
    fun parsesEmptyArray() {
      server.stubFirstPollWithOffenderEvents("[]")

      val events = service.getEvents(LocalDateTime.of(2020, 1, 1, 2, 30, 0), LocalDateTime.of(2020, 1, 1, 3, 30, 0))

      assertThat(events).isEmpty()
    }

    @Test
    @DisplayName("Parses single object with known fields")
    fun parsesSingleObjectWithKnownFields() {
      server.stubFirstPollWithOffenderEvents(
        """[{
          "eventType":"OFFENDER_MOVEMENT-DISCHARGE",
          "eventDatetime":"2021-02-08T14:41:11.526762",
          "offenderIdDisplay":"A5194DY",
          "bookingId":1201234,
          "movementSeq":11,
          "nomisEventType":"OFF_DISCH_OASYS"
        }]
        """
      )

      val events = service.getEvents(LocalDateTime.of(2020, 1, 1, 2, 30, 0), LocalDateTime.of(2020, 1, 1, 3, 30, 0))

      assertThat(events).usingRecursiveFieldByFieldElementComparator().containsExactly(
        OffenderEvent.builder()
          .eventType("OFFENDER_MOVEMENT-DISCHARGE")
          .eventDatetime(LocalDateTime.parse("2021-02-08T14:41:11.526762"))
          .offenderIdDisplay("A5194DY")
          .bookingId(1201234)
          .movementSeq(11)
          .nomisEventType("OFF_DISCH_OASYS")
          .additionalFields(mapOf())
          .build()
      )
    }

    @Test
    @DisplayName("Parses object with additional unknown fields")
    fun parsesObjectWithAdditionalUnknownFields() {
      server.stubFirstPollWithOffenderEvents(
        """[{
            "eventType":"OFFENDER_MOVEMENT-DISCHARGE",
            "eventDatetime":"2021-02-08T14:41:11.526762",
            "offenderIdDisplay":"A5194DY",
            "bookingId":1201234,
            "movementSeq":11,
            "nomisEventType":"OFF_DISCH_OASYS",
            "number":2,
            "boolean":true,
            "string":"some-string",
            "list":[3,"string",true],
            "map":{
              "some-key":3,
              "nested-list":[7,8,9]
            }
          }]
        """
      )

      val events = service.getEvents(LocalDateTime.of(2020, 1, 1, 2, 30, 0), LocalDateTime.of(2020, 1, 1, 3, 30, 0))

      assertThat(events).usingRecursiveFieldByFieldElementComparator().containsExactly(
        OffenderEvent.builder()
          .eventType("OFFENDER_MOVEMENT-DISCHARGE")
          .eventDatetime(LocalDateTime.parse("2021-02-08T14:41:11.526762"))
          .offenderIdDisplay("A5194DY")
          .bookingId(1201234)
          .movementSeq(11)
          .nomisEventType("OFF_DISCH_OASYS")
          .additionalFields(
            mapOf(
              "number" to 2,
              "boolean" to true,
              "string" to "some-string",
              "list" to listOf(3, "string", true),
              "map" to mapOf(
                "some-key" to 3,
                "nested-list" to listOf(7, 8, 9)
              )
            )
          )
          .build()
      )
    }
  }
}
