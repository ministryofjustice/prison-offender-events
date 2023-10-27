package uk.gov.justice.hmpps.offenderevents.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import uk.gov.justice.hmpps.offenderevents.resource.QueueListenerIntegrationTest
import uk.gov.justice.hmpps.offenderevents.services.wiremock.HMPPSAuthExtension
import uk.gov.justice.hmpps.offenderevents.services.wiremock.PrisonApiExtension
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Consumer

@ExtendWith(PrisonApiExtension::class, HMPPSAuthExtension::class)
class HealthCheckTest : QueueListenerIntegrationTest() {

  @BeforeEach
  fun setUp() {
    stubHealthPing(200)
    purgeQueues()
  }

  @Test
  fun `Health page reports ok`() {
    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
      .jsonPath("components.OAuthApiHealth.details.HttpStatus").isEqualTo("OK")
      .jsonPath("components.prisonApiHealth.details.HttpStatus").isEqualTo("OK")
  }

  @Test
  fun `Health page reports down`() {
    stubHealthPing(404)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("status").isEqualTo("DOWN")
      .jsonPath("components.OAuthApiHealth.details.HttpStatus").isEqualTo("NOT_FOUND")
      .jsonPath("components.prisonApiHealth.details.HttpStatus").isEqualTo("NOT_FOUND")
  }

  @Test
  fun `Health page reports a teapot`() {
    stubHealthPing(418)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("components.OAuthApiHealth.details.HttpStatus").isEqualTo("I_AM_A_TEAPOT")
      .jsonPath("components.prisonApiHealth.details.HttpStatus").isEqualTo("I_AM_A_TEAPOT")
      .jsonPath("status").isEqualTo("DOWN")
  }

  @Test
  fun `Queue health reports queue details`() {
    webTestClient.get().uri("/health")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("components.prisoneventqueue-health.details.queueName").isEqualTo(prisonEventQueueName)
      .jsonPath("components.prisoneventqueue-health.details.messagesOnQueue").isEqualTo(0)
      .jsonPath("components.prisoneventqueue-health.details.messagesInFlight").isEqualTo(0)
      .jsonPath("components.prisoneventqueue-health.details.messagesOnDlq").isEqualTo(0)
      .jsonPath("components.prisoneventqueue-health.details.dlqStatus").isEqualTo("UP")
      .jsonPath("components.prisoneventqueue-health.details.dlqName").isEqualTo(prisonEventDlqName)
  }

  @Test
  fun `Health info reports version`() {
    webTestClient.get().uri("/health")
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("components.healthInfo.details.version").value(
        Consumer<String> {
          assertThat(it).startsWith(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE))
        },
      )
  }

  @Test
  fun `Health ping page is accessible`() {
    webTestClient.get()
      .uri("/health/ping")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `readiness reports ok`() {
    webTestClient.get()
      .uri("/health/readiness")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `liveness reports ok`() {
    webTestClient.get()
      .uri("/health/liveness")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  fun stubHealthPing(status: Int) {
    HMPPSAuthExtension.server.stubHealthPing(status)
    PrisonApiExtension.server.stubHealthPing(status)
  }
}
