package uk.gov.justice.hmpps.offenderevents.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import kotlin.apply as kotlinApply

@Configuration
class WebClientConfiguration(
  @Value("\${oauth.api.base.url}") val hmppsAuthBaseUrl: String,
  @Value("\${prison.api.base.url}") val prisonApiBaseUrl: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:20s}") val timeout: Duration,
) {

  @Bean
  fun prisonApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.authorisedWebClient(authorizedClientManager, registrationId = "prison-api", url = prisonApiBaseUrl, timeout)

  @Bean
  fun hmppsAuthHealthWebClient(builder: WebClient.Builder): WebClient =
    builder.healthWebClient(hmppsAuthBaseUrl, healthTimeout)

  @Bean
  fun prisonApiHealthWebClient(builder: WebClient.Builder): WebClient =
    builder.healthWebClient(prisonApiBaseUrl, healthTimeout)

  @Bean
  fun authorizedClientManager(
    clientRegistrationRepository: ClientRegistrationRepository,
    oAuth2AuthorizedClientService: OAuth2AuthorizedClientService,
  ): OAuth2AuthorizedClientManager = AuthorizedClientServiceOAuth2AuthorizedClientManager(
    clientRegistrationRepository,
    oAuth2AuthorizedClientService,
  ).kotlinApply {
    setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build())
  }
}

fun WebClient.Builder.authorisedWebClient(
  authorizedClientManager: OAuth2AuthorizedClientManager,
  registrationId: String,
  url: String,
  timeout: Duration,
): WebClient =
  baseUrl(url)
    .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(timeout)))
    .exchangeStrategies(ExchangeStrategies.builder().codecs { it.defaultCodecs().maxInMemorySize(-1) }.build())
    .filter(
      ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).kotlinApply {
        setDefaultClientRegistrationId(registrationId)
      },
    )
    .build()

fun WebClient.Builder.healthWebClient(url: String, healthTimeout: Duration): WebClient =
  baseUrl(url)
    .clientConnector(ReactorClientHttpConnector(HttpClient.create().responseTimeout(healthTimeout)))
    .build()
