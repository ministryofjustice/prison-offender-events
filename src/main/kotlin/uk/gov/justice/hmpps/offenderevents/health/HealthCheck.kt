package uk.gov.justice.hmpps.offenderevents.health

import lombok.AccessLevel
import lombok.AllArgsConstructor
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.lang.Exception
import java.time.Duration

@AllArgsConstructor(access = AccessLevel.PROTECTED)
abstract class HealthCheck(
  private val webClient: WebClient,
  private val baseUri: String,
  private val timeout: Duration
) : HealthIndicator {

  override fun health(): Health? {
    return webClient.get()
      .uri("$baseUri/health/ping")
      .retrieve()
      .toEntity(String::class.java)
      .flatMap { Mono.just(Health.up().withDetail("HttpStatus", it?.statusCode).build()) }
      .onErrorResume(WebClientResponseException::class.java) { Mono.just(Health.down(it).withDetail("body", it.responseBodyAsString).withDetail("HttpStatus", it.statusCode).build()) }
      .onErrorResume(Exception::class.java) { Mono.just(Health.down(it).build()) }
      .block(timeout)
  }
}
