package uk.gov.justice.hmpps.offenderevents.config;

import org.springframework.beans.factory.annotation.Qualifier;
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

@Configuration
public class RestTemplateConfiguration {

    private final OffenderEventsProperties properties;
    private final OAuth2ClientContext oauth2ClientContext;
    private final ClientCredentialsResourceDetails elite2apiDetails;
    private final ClientCredentialsResourceDetails custodyapiDetails;

    public RestTemplateConfiguration(final OffenderEventsProperties properties,
                                     final OAuth2ClientContext oauth2ClientContext,
                                     @Qualifier("elite2apiDetails") final ClientCredentialsResourceDetails elite2apiDetails,
                                     @Qualifier("custodyapiDetails") final ClientCredentialsResourceDetails custodyapiDetails) {
        this.properties = properties;
        this.oauth2ClientContext = oauth2ClientContext;
        this.elite2apiDetails = elite2apiDetails;
        this.custodyapiDetails = custodyapiDetails;
    }

    @Bean(name = "oauthApiRestTemplate")
    public RestTemplate oauthApiRestTemplate(final RestTemplateBuilder restTemplateBuilder) {
        return getRestTemplate(restTemplateBuilder, properties.getOauthApiBaseUrl());
    }

    @Bean(name = "elite2apiHealthRestTemplate")
    public RestTemplate elite2apiHealthRestTemplate(final RestTemplateBuilder restTemplateBuilder) {
        return getRestTemplate(restTemplateBuilder, properties.getElite2ApiBaseUrl());
    }

    @Bean(name = "custodyapiHealthRestTemplate")
    public RestTemplate custodyapiHealthRestTemplate(final RestTemplateBuilder restTemplateBuilder) {
        return getRestTemplate(restTemplateBuilder, properties.getCustodyApiBaseUrl());
    }

    @Bean(name = "elite2apiRestTemplate")
    public OAuth2RestTemplate elite2apiRestTemplate(final GatewayAwareAccessTokenProvider accessTokenProvider) {
        return getAuth2RestTemplate(accessTokenProvider, elite2apiDetails, properties.getElite2ApiBaseUrl() + "/api");
    }

    @Bean(name = "custodyapiRestTemplate")
    public OAuth2RestTemplate custodyapiRestTemplate(final GatewayAwareAccessTokenProvider accessTokenProvider) {
        return getAuth2RestTemplate(accessTokenProvider, custodyapiDetails, properties.getCustodyApiBaseUrl() + "/api");
    }

    private OAuth2RestTemplate getAuth2RestTemplate(final GatewayAwareAccessTokenProvider accessTokenProvider, final ClientCredentialsResourceDetails clientCredentialsResourceDetails, final String rootUri) {
        final var elite2SystemRestTemplate = new OAuth2RestTemplate(clientCredentialsResourceDetails, oauth2ClientContext);

        elite2SystemRestTemplate.setAccessTokenProvider(accessTokenProvider);

        RootUriTemplateHandler.addTo(elite2SystemRestTemplate, rootUri);
        return elite2SystemRestTemplate;
    }

    private RestTemplate getRestTemplate(final RestTemplateBuilder restTemplateBuilder, final String uri) {
        return restTemplateBuilder
                .rootUri(uri)
                .additionalInterceptors(new JwtAuthInterceptor())
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
