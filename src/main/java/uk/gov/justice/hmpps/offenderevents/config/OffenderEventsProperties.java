package uk.gov.justice.hmpps.offenderevents.config;

import lombok.Getter;
import org.hibernate.validator.constraints.URL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@Validated
@Getter
public class OffenderEventsProperties {

    /**
     * Prison API Base URL endpoint ("http://localhost:8081")
     */
    private final String prisonApiBaseUrl;

    /**
     * Prison API Base URL endpoint ("http://localhost:8082")
     */
    private final String communityApiBaseUrl;


    /**
     * OAUTH2 API Rest URL endpoint ("http://localhost:9090/auth")
     */
    private final String oauthApiBaseUrl;

    /**
     * Case Notes API Rest URL endpoint ("http://localhost:8083")
     */
    private final String casenotesApiBaseUrl;

    public OffenderEventsProperties(@Value("${prison.api.base.url}") @URL final String prisonApiBaseUrl,
                                    @Value("${community.api.base.url}") @URL final String communityApiBaseUrl,
                                    @Value("${oauth.api.base.url}") @URL final String oauthApiBaseUrl,
                                    @Value("${casenotes.api.base.url}") @URL final String casenotesApiBaseUrl) {
        this.prisonApiBaseUrl = prisonApiBaseUrl;
        this.communityApiBaseUrl = communityApiBaseUrl;
        this.oauthApiBaseUrl = oauthApiBaseUrl;
        this.casenotesApiBaseUrl = casenotesApiBaseUrl;
    }
}
