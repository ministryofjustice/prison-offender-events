package uk.gov.justice.hmpps.offenderevents.config;


import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.DefaultOAuth2ClientContext;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.token.grant.client.ClientCredentialsResourceDetails;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.DefaultTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;
import springfox.documentation.builders.AuthorizationCodeGrantBuilder;
import springfox.documentation.builders.OAuthBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.*;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Configuration
@EnableSwagger2
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtLeastFor = "PT10S", defaultLockAtMostFor = "PT12H")
public class ResourceServerConfiguration extends ResourceServerConfigurerAdapter {

    private final OffenderEventsProperties properties;

    @Autowired(required = false)
    private BuildProperties buildProperties;

    public ResourceServerConfiguration(final OffenderEventsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void configure(final HttpSecurity http) throws Exception {

        http.headers().frameOptions().sameOrigin().and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

                // Can't have CSRF protection as requires session
                .and().csrf().disable()
                .authorizeRequests()
                .antMatchers("/webjars/**", "/favicon.ico", "/csrf",
                        "/health", "/info", "/ping", "/h2-console/**",
                        "/v2/api-docs",
                        "/swagger-ui.html", "/swagger-resources", "/swagger-resources/configuration/ui",
                        "/swagger-resources/configuration/security").permitAll()
                .anyRequest()
                .authenticated();
    }

    @Override
    public void configure(final ResourceServerSecurityConfigurer config) {
        config.tokenServices(tokenServices());
    }

    @Bean
    public TokenStore tokenStore() {
        return new JwtTokenStore(accessTokenConverter());
    }

    @Bean
    public JwtAccessTokenConverter accessTokenConverter() {
        final var converter = new JwtAccessTokenConverter();
        converter.setVerifierKey(new String(Base64.decodeBase64(properties.getJwtPublicKey())));
        return converter;
    }

    @Bean
    @Primary
    public DefaultTokenServices tokenServices() {
        final var defaultTokenServices = new DefaultTokenServices();
        defaultTokenServices.setTokenStore(tokenStore());
        return defaultTokenServices;
    }

    @Bean
    public Docket api() {

        final var docket = new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.basePackage("uk.gov.justice.hmpps.offenderevents.controllers"))
                .paths(PathSelectors.any())
                .build()
                .securitySchemes(List.of(securityScheme()))
                .securityContexts(List.of(securityContext()))
                .apiInfo(apiInfo());

        docket.genericModelSubstitutes(Optional.class);
        docket.directModelSubstitute(ZonedDateTime.class, java.util.Date.class);
        docket.directModelSubstitute(LocalDateTime.class, java.util.Date.class);
        return docket;
    }

    private SecurityScheme securityScheme() {
        final var grantType = new AuthorizationCodeGrantBuilder()
                .tokenEndpoint(new TokenEndpoint("http://localhost:9090/auth/oauth" + "/token", "oauthtoken"))
                .tokenRequestEndpoint(
                        new TokenRequestEndpoint("http://localhost:9090/auth/oauth" + "/authorize", "swagger-client", "clientsecret"))
                .build();

        return new OAuthBuilder().name("spring_oauth")
                .grantTypes(List.of(grantType))
                .scopes(List.of(scopes()))
                .build();
    }

    private AuthorizationScope[] scopes() {
        return new AuthorizationScope[]{
                new AuthorizationScope("read", "for read operations"),
                new AuthorizationScope("write", "for write operations")
        };
    }

    private SecurityContext securityContext() {
        return SecurityContext.builder()
                .securityReferences(
                        List.of(new SecurityReference("spring_oauth", scopes())))
                .forPaths(PathSelectors.regex("/.*"))
                .build();
    }

    private String getVersion() {
        return buildProperties == null ? "version not available" : buildProperties.getVersion();
    }

    private Contact contactInfo() {
        return new Contact(
                "HMPPS Digital Studio",
                "",
                "feedback@digital.justice.gov.uk");
    }

    private ApiInfo apiInfo() {
        final var vendorExtension = new StringVendorExtension("", "");
        final Collection<VendorExtension> vendorExtensions = new ArrayList<>();
        vendorExtensions.add(vendorExtension);

        return new ApiInfo(
                "HMPPS Offender Events Service Documentation",
                "API for Surfacing Events from Offenders in Nomis.",
                getVersion(),
                "https://gateway.nomis-api.service.justice.gov.uk/auth/terms",
                contactInfo(),
                "MIT", "http://www.apache.org/licenses", vendorExtensions);
    }

    @Bean
    public OAuth2ClientContext oAuth2ClientContext() {
        return new DefaultOAuth2ClientContext();
    }

    @Bean("custodyapiDetails")
    @ConfigurationProperties("custodyapi.client")
    public ClientCredentialsResourceDetails custodyapiiDetails() {
        return new ClientCredentialsResourceDetails();
    }

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
