package uk.gov.justice.hmpps.offenderevents.config

import org.hibernate.validator.constraints.URL
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.validation.annotation.Validated

@Configuration
@Validated
class OffenderEventsProperties(
  /**
   * Prison API Base URL endpoint ("http://localhost:8081")
   */
  @Value("\${prison.api.base.url}") val prisonApiBaseUrl: @URL String,
  /**
   * Prison API Base URL endpoint ("http://localhost:8082")
   */
  @Value("\${community.api.base.url}") val communityApiBaseUrl: @URL String,
  /**
   * OAUTH2 API Rest URL endpoint ("http://localhost:9090/auth")
   */
  @Value("\${oauth.api.base.url}") val oauthApiBaseUrl: @URL String,
  /**
   * Case Notes API Rest URL endpoint ("http://localhost:8083")
   */
  @Value("\${casenotes.api.base.url}") val casenotesApiBaseUrl: @URL String,
)
