package uk.gov.justice.hmpps.offenderevents.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfiguration {

    private final OffenderEventsProperties properties;

    public WebClientConfiguration(final OffenderEventsProperties properties) {
        this.properties = properties;
    }

    @Bean
    WebClient prisonApiWebClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        final var oauth2Client = new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Client.setDefaultClientRegistrationId("prison-api");
        return WebClient.builder()
            .baseUrl(properties.getPrisonApiBaseUrl())
            .apply(oauth2Client.oauth2Configuration())
            .exchangeStrategies(ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                    .maxInMemorySize(-1))
                .build())
            .build();
    }

    @Bean
    WebClient communityApiWebClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        final var oauth2Client = new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Client.setDefaultClientRegistrationId("community-api");
        return WebClient.builder()
            .baseUrl(properties.getCommunityApiBaseUrl())
            .apply(oauth2Client.oauth2Configuration())
            .exchangeStrategies(ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                    .maxInMemorySize(1024 * 1024 ))
                .build())
            .build();
    }

    @Bean
    WebClient webClient() {
        return WebClient.builder().build();
    }

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
                                                          OAuth2AuthorizedClientService oAuth2AuthorizedClientService) {
        final var authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build();
        final var authorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, oAuth2AuthorizedClientService);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
        return authorizedClientManager;
    }

}
