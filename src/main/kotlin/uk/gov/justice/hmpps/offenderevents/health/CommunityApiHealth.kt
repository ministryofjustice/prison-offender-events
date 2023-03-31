package uk.gov.justice.hmpps.offenderevents.health

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.offenderevents.config.OffenderEventsProperties
import java.time.Duration

@Component
class CommunityApiHealth(
  healthCheckWebClient: WebClient,
  properties: OffenderEventsProperties,
  @Value("\${api.health-timeout:1s}") healthTimeout: Duration,
) : HealthCheck(healthCheckWebClient, properties.communityApiBaseUrl, healthTimeout)
