package uk.gov.justice.hmpps.offenderevents.config

import org.hibernate.validator.constraints.URL
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.validation.annotation.Validated

@Configuration
@Validated
class OffenderEventsProperties(
  /**
   * Case Notes API Rest URL endpoint ("http://localhost:8083")
   */
  @Value("\${api.base.url.casenotes}") val casenotesApiBaseUrl: @URL String,
)
