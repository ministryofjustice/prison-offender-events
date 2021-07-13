package uk.gov.justice.hmpps.offenderevents.smoketest

import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Signal
import uk.gov.justice.hmpps.offenderevents.smoketest.SmokeTest.TestProgress.SUCCESS
import java.time.Duration

@SpringBootTest(classes = [SmokeTestConfiguration::class])
@ActiveProfiles("smoke-test")
class SmokeTest {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Autowired
  private lateinit var smokeTestWebClient: WebClient

  @Test
  internal fun `will release a prisoner`() {
    val results = runBlocking {
      withTimeout(Duration.ofMinutes(5).toMillis()) {
        waitForResults()
      }
    }
    assertThat(results.progress)
      .withFailMessage(results.toString())
      .isEqualTo(SUCCESS)
  }

  suspend fun waitForResults(): TestStatus = smokeTestWebClient.post()
    .uri("smoke-test/prison-offender-events/POE_T3")
    .retrieve()
    .bodyToFlux(TestStatus::class.java)
    .doOnError { log.error("Received error while waiting for results", it) }
    .doOnEach(this::logUpdate)
    .awaitLast()

  private fun logUpdate(signal: Signal<TestStatus>) {
    signal.let { it.get()?.let { result -> println(result.description) } }
  }

  data class TestStatus(val description: String, val progress: TestProgress)
  enum class TestProgress { INCOMPLETE, COMPLETE, SUCCESS, FAIL; }
}
