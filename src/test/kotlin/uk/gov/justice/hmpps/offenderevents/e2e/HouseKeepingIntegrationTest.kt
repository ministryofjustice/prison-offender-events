package uk.gov.justice.hmpps.offenderevents.e2e

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.hmpps.offenderevents.resource.QueueListenerIntegrationTest

class HouseKeepingIntegrationTest : QueueListenerIntegrationTest() {
  @BeforeEach
  internal fun setUp() {
    purgeQueues()
  }

  @Test
  fun `housekeeping will consume a booking changed message on the dlq and return to main queue`() {
    val message = "/messages/bookingNumberChanged.json".readResourceAsText()

    awsSqsClient.sendMessage(dlqUrl, message)

    webTestClient.put()
      .uri("/queue-admin/retry-all-dlqs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk

    await untilCallTo { getNumberOfMessagesCurrentlyOnDlq() } matches { it == 0 }
    await untilCallTo { getNumberOfMessagesCurrentlyOnQueue() } matches { it == 0 }
  }
}

private fun String.readResourceAsText(): String {
  return HouseKeepingIntegrationTest::class.java.getResource(this).readText()
}
