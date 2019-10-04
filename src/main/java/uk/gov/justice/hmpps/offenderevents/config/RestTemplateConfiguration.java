package uk.gov.justice.hmpps.offenderevents.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import uk.gov.justice.hmpps.offenderevents.utils.JwtAuthInterceptor;
import uk.gov.justice.hmpps.offenderevents.utils.W3cTracingInterceptor;

import java.util.List;

@Configuration
public class RestTemplateConfiguration {

    private final OffenderEventsProperties properties;

    public RestTemplateConfiguration(final OffenderEventsProperties properties) {
        this.properties = properties;
    }

    @Bean(name = "elite2ApiRestTemplate")
    public RestTemplate elite2ApiRestTemplate(final RestTemplateBuilder restTemplateBuilder) {
        return getRestTemplate(restTemplateBuilder, properties.getElite2ApiBaseUrl());
    }

    @Bean(name = "oauthApiRestTemplate")
    public RestTemplate oauthApiRestTemplate(final RestTemplateBuilder restTemplateBuilder) {
        return getRestTemplate(restTemplateBuilder, properties.getOauthApiBaseUrl());
    }


    private RestTemplate getRestTemplate(final RestTemplateBuilder restTemplateBuilder, final String uri) {
        return restTemplateBuilder
                .rootUri(uri)
                .additionalInterceptors(getRequestInterceptors())
                .build();
    }

    private List<ClientHttpRequestInterceptor> getRequestInterceptors() {
        return List.of(
                new W3cTracingInterceptor(),
                new JwtAuthInterceptor());
    }
}
