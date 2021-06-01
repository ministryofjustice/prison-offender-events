package uk.gov.justice.hmpps.offenderevents.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;


@Configuration
@EnableWebSecurity
public class ResourceServerConfiguration extends WebSecurityConfigurerAdapter {

    @Override
    public void configure(final HttpSecurity http) throws Exception {

        http.headers().frameOptions().sameOrigin().and()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

            // Can't have CSRF protection as requires session
            .and().csrf().disable()
            .authorizeRequests(auth ->
                auth.antMatchers("/webjars/**", "/favicon.ico", "/csrf",
                    "/health/**", "/info", "/h2-console/**",
                    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
                ).permitAll()
                    .anyRequest()
                    .authenticated());

    }


}
