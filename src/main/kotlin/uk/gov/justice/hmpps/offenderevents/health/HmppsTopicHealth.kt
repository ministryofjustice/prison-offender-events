package uk.gov.justice.hmpps.offenderevents.health

import com.amazonaws.services.sns.AmazonSNS
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.Health.Builder
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import uk.gov.justice.hmpps.offenderevents.config.SqsConfigProperties
import uk.gov.justice.hmpps.offenderevents.config.hmppsEventTopic
import java.lang.Exception

@Component
class HmppsTopicHealth(
  @param:Qualifier("awsHMPPSEventsSnsClient") private val awsHmppsEventsSnsClient: AmazonSNS,
  sqsConfigProperties: SqsConfigProperties
) : HealthIndicator {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
  private val hmppsArn = sqsConfigProperties.hmppsEventTopic().topicArn

  override fun health(): Health {
    return try {
      awsHmppsEventsSnsClient.getTopicAttributes(hmppsArn)
      Builder().up().withDetail("hmppsArn", hmppsArn).build()
    } catch (ex: Exception) {
      log.error("Health failed for SNS Topic due to ", ex)
      Builder().down(ex).withDetail("hmppsArn", hmppsArn).build()
    }
  }
}
