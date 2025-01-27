package uk.gov.justice.hmpps.offenderevents.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.healthWebClient
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @Value("\${api.base.url.hmpps-auth}") val hmppsAuthBaseUrl: String,
  @Value("\${api.base.url.prison}") val prisonApiBaseUrl: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:20s}") val timeout: Duration,
) {

  @Bean
  fun prisonApiWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.authorisedWebClient(
    authorizedClientManager = authorizedClientManager,
    registrationId = "prison-api",
    url = prisonApiBaseUrl,
    timeout = timeout,
  )

  @Bean
  fun hmppsAuthHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(hmppsAuthBaseUrl, healthTimeout)

  @Bean
  fun prisonApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(prisonApiBaseUrl, healthTimeout)
}
