package uk.gov.justice.hmpps.offenderevents.services

import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.LocalDate

@Service
@Slf4j
class CommunityApiService(
  private val communityApiWebClient: WebClient,
  @Value("\${api.community-timeout:30s}") private val timeout: Duration,
) {
  fun getRecalls(offenderNumber: String?): List<Recall> =
    communityApiWebClient.get()
      .uri("/secure/offenders/nomsNumber/$offenderNumber/convictions/active/nsis/recall")
      .retrieve()
      .bodyToMono(NsiWrapper::class.java)
      .onErrorResume(WebClientResponseException::class.java) { emptyWhenNotFound(it) }
      .block(timeout)
      ?.nsis ?: emptyList()

  private fun <T> emptyWhenNotFound(exception: WebClientResponseException): Mono<T> =
    if (exception.statusCode == HttpStatus.NOT_FOUND) {
      Mono.empty()
    } else {
      Mono.error(exception)
    }
}

data class NsiWrapper(val nsis: List<Recall>)
data class Recall(val referralDate: LocalDate, val recallRejectedOrWithdrawn: Boolean?, val outcomeRecall: Boolean?)
