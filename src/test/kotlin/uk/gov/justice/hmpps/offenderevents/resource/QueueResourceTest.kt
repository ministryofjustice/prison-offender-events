package uk.gov.justice.hmpps.offenderevents.resource

import com.amazonaws.services.sqs.model.PurgeQueueRequest
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.MediaType
import uk.gov.justice.hmpps.offenderevents.config.prisonEventQueue

class QueueResourceTest : QueueListenerIntegrationTest() {

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class SecureEndpoints {
    private fun secureEndpoints() =
      listOf(
        "/queue-admin/purge-queue/any",
        "/queue-admin/retry-dlq/any",
      )

    @ParameterizedTest
    @MethodSource("secureEndpoints")
    internal fun `requires a valid authentication token`(uri: String) {
      webTestClient.put()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isUnauthorized
    }
  }
  @Test
  fun `should fail if no token`() {
    webTestClient.put()
      .uri("/queue-admin/retry-dlq/${sqsConfigProperties.prisonEventQueue().dlqName}")
      .contentType(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `should fail if wrong role`() {
    webTestClient.put()
      .uri("/queue-admin/retry-dlq/${sqsConfigProperties.prisonEventQueue().dlqName}")
      .headers(setAuthorisation(roles = listOf("ROLE_WRONG")))
      .contentType(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `should fail it dlq does not exist`() {
    webTestClient.put()
      .uri("/queue-admin/retry-dlq/UNKNOWN_DLQ")
      .headers(setAuthorisation(roles = listOf("ROLE_QUEUE_ADMIN")))
      .contentType(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isNotFound
  }

  @Test
  fun `should purge message from the DLQ`() {
    val message = """
    {
      "what": "OFFENDER_DELETED",
      "when": "2021-01-25T12:30:00Z",
      "operationId": "badea6d876c62e2f5264c94c7b50875e",
      "who": "bobby.beans",
      "service": "offender-service",
      "details": "{ \"offenderId\": \"99\"}"
    }
  """
    awsSqsClient.purgeQueue(PurgeQueueRequest(queueUrl))
    awsSqsDlqClient.purgeQueue(PurgeQueueRequest(dlqUrl))

    await untilCallTo { getNumberOfMessagesCurrentlyOnDlq() } matches { it == 0 }
    awsSqsDlqClient.sendMessage(dlqUrl, message)
    await untilCallTo { getNumberOfMessagesCurrentlyOnDlq() } matches { it == 1 }

    webTestClient.put()
      .uri("/queue-admin/purge-queue/${sqsConfigProperties.prisonEventQueue().dlqName}")
      .headers(setAuthorisation(roles = listOf("ROLE_QUEUE_ADMIN")))
      .contentType(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk

    await untilCallTo { getNumberOfMessagesCurrentlyOnDlq() } matches { it == 0 }
  }
}
