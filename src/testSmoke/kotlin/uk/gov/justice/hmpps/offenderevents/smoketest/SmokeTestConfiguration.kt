package uk.gov.justice.hmpps.offenderevents.smoketest

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.Builder

@ConditionalOnProperty(name = ["smoketest.enabled"], havingValue = "true")
@EnableWebSecurity
@Import(OAuth2ClientAutoConfiguration::class)
class SmokeTestConfiguration(@Value("\${smoketest.endpoint.url}") private val smokeTestUrl: String) {
  private val webClientBuilder: Builder = WebClient.builder()

  @Bean
  fun smokeTestWebClient(authorizedClientManager: OAuth2AuthorizedClientManager): WebClient = webClientBuilder
    .baseUrl(smokeTestUrl)
    .apply(
      ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager).also {
        it.setDefaultClientRegistrationId("smoketest-service")
      }.oauth2Configuration(),
    )
    .build()

  @Bean
  fun authorizedClientManager(
    clientRegistrationRepository: ClientRegistrationRepository,
    oAuth2AuthorizedClientService: OAuth2AuthorizedClientService,
  ): OAuth2AuthorizedClientManager =
    AuthorizedClientServiceOAuth2AuthorizedClientManager(
      clientRegistrationRepository,
      oAuth2AuthorizedClientService,
    ).apply {
      setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build())
    }
}
