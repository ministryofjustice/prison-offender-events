package uk.gov.justice.hmpps.offenderevents.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RootUriTemplateHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2RestTemplate;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsAccessTokenProvider;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.hmpps.offenderevents.utils.JwtAuthInterceptor;

import java.time.Duration;

@Configuration
public class RestTemplateConfiguration {

    private final OffenderEventsProperties properties;
    private final OAuth2ClientContext oauth2ClientContext;
    private final ClientCredentialsResourceDetails custodyapiDetails;
    private final Duration healthTimeout;

    public RestTemplateConfiguration(final OffenderEventsProperties properties,
                                     final OAuth2ClientContext oauth2ClientContext,
                                     @Qualifier("custodyapiDetails") final ClientCredentialsResourceDetails custodyapiDetails,
                                     @Value("${api.health-timeout:1s}") final Duration healthTimeout) {
        this.properties = properties;
        this.oauth2ClientContext = oauth2ClientContext;
        this.custodyapiDetails = custodyapiDetails;
        this.healthTimeout = healthTimeout;
    }

    @Bean(name = "oauthApiRestTemplate")
    public RestTemplate oauthApiRestTemplate(final RestTemplateBuilder restTemplateBuilder) {
        return getRestTemplate(restTemplateBuilder, properties.getOauthApiBaseUrl());
    }

    @Bean(name = "custodyapiHealthRestTemplate")
    public RestTemplate custodyapiHealthRestTemplate(final RestTemplateBuilder restTemplateBuilder) {
        return getHealthRestTemplate(restTemplateBuilder, properties.getCustodyApiBaseUrl());
    }

    @Bean(name = "custodyapiRestTemplate")
    public OAuth2RestTemplate custodyapiRestTemplate(final GatewayAwareAccessTokenProvider accessTokenProvider) {
        return getAuth2RestTemplate(accessTokenProvider, custodyapiDetails, properties.getCustodyApiBaseUrl() + "/api");
    }

    private OAuth2RestTemplate getAuth2RestTemplate(final GatewayAwareAccessTokenProvider accessTokenProvider, final ClientCredentialsResourceDetails clientCredentialsResourceDetails, final String rootUri) {
        final var systemRestTemplate = new OAuth2RestTemplate(clientCredentialsResourceDetails, oauth2ClientContext);
        systemRestTemplate.setAccessTokenProvider(accessTokenProvider);

        RootUriTemplateHandler.addTo(systemRestTemplate, rootUri);
        return systemRestTemplate;
    }

    private RestTemplate getRestTemplate(final RestTemplateBuilder restTemplateBuilder, final String uri) {
        return restTemplateBuilder
                .rootUri(uri)
                .additionalInterceptors(new JwtAuthInterceptor())
                .build();
    }

    private RestTemplate getHealthRestTemplate(final RestTemplateBuilder restTemplateBuilder, final String uri) {
        return restTemplateBuilder
                .rootUri(uri)
                .additionalInterceptors(new JwtAuthInterceptor())
                .setConnectTimeout(healthTimeout)
                .setReadTimeout(healthTimeout)
                .build();
    }

    /**
     * This subclass is necessary to make OAuth2AccessTokenSupport.getRestTemplate() public
     */
    @Component("accessTokenProvider")
    public static class GatewayAwareAccessTokenProvider extends ClientCredentialsAccessTokenProvider {
        @Override
        public RestOperations getRestTemplate() {
            return super.getRestTemplate();
        }
    }
}
