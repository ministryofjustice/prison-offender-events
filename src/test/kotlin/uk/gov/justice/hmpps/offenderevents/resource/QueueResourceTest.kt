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
      .uri("/queue-admin/retry-dlq/anyDlq")
      .contentType(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `should fail if wrong role`() {
    webTestClient.put()
      .uri("/queue-admin/retry-dlq/anyDlq")
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
    prisonEventQueueSqsClient.purgeQueue(PurgeQueueRequest(prisonEventQueueUrl))
    prisonEventSqsDlqClient.purgeQueue(PurgeQueueRequest(prisonEventDlqUrl))

    await untilCallTo { getNumberOfMessagesCurrentlyOnPrisonEventDlq() } matches { it == 0 }
    prisonEventSqsDlqClient.sendMessage(prisonEventDlqUrl, message)
    await untilCallTo { getNumberOfMessagesCurrentlyOnPrisonEventDlq() } matches { it == 1 }

    webTestClient.put()
      .uri("/queue-admin/purge-queue/$prisonEventDlqName")
      .headers(setAuthorisation(roles = listOf("ROLE_QUEUE_ADMIN")))
      .contentType(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk

    await untilCallTo { getNumberOfMessagesCurrentlyOnPrisonEventDlq() } matches { it == 0 }
  }
}
