package uk.gov.justice.hmpps.offenderevents.health

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Component

/**
 * Adds version data to the /health endpoint. This is called by the UI to display API details
 */
@Component
class HealthInfo(@param:Autowired(required = false) private val buildProperties: BuildProperties?) : HealthIndicator {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val version: String
    get() = if (buildProperties == null) "version not available" else buildProperties.version

  override fun health(): Health {
    val status = Health.up().withDetail("version", version).build()
    log.info(status.toString())
    return status
  }
}
