package uk.gov.justice.hmpps.offenderevents.config;


import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.service.StringVendorExtension;
import springfox.documentation.service.VendorExtension;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

@Configuration
@EnableSwagger2
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableScheduling
@EnableWebSecurity
@EnableSchedulerLock(defaultLockAtLeastFor = "PT10S", defaultLockAtMostFor = "PT10M")
public class ResourceServerConfiguration extends WebSecurityConfigurerAdapter {

    @Autowired(required = false)
    private BuildProperties buildProperties;

    @Override
    public void configure(final HttpSecurity http) throws Exception {

        http.headers().frameOptions().sameOrigin().and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

                // Can't have CSRF protection as requires session
                .and().csrf().disable()
                .authorizeRequests(auth ->
                        auth.antMatchers("/webjars/**", "/favicon.ico", "/csrf",
                                "/health", "/info", "/h2-console/**",
                                "/v2/api-docs",
                                "/swagger-ui.html", "/swagger-resources", "/swagger-resources/configuration/ui",
                                "/swagger-resources/configuration/security").permitAll()
                                .anyRequest()
                                .authenticated()).oauth2ResourceServer()
                .jwt();

    }


    @Bean
    public Docket api() {

        final var docket = new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.basePackage("uk.gov.justice.hmpps.offenderevents.controllers"))
                .paths(PathSelectors.any())
                .build()
                .apiInfo(apiInfo());

        docket.genericModelSubstitutes(Optional.class);
        docket.directModelSubstitute(ZonedDateTime.class, java.util.Date.class);
        docket.directModelSubstitute(LocalDateTime.class, java.util.Date.class);
        return docket;
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
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
