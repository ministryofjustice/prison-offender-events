package uk.gov.justice.hmpps.offenderevents.health

import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.GetTopicAttributesResult
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.actuate.health.Status
import uk.gov.justice.hmpps.offenderevents.config.SqsConfigProperties
import java.lang.RuntimeException

internal class TopicHealthTest {
  private val awsPrisonEventsSnsClient = Mockito.mock(AmazonSNS::class.java)
  private val topicHealth = TopicHealth(
    awsPrisonEventsSnsClient,
    SqsConfigProperties(
      "", "", queues = mapOf("prisonEventQueue" to SqsConfigProperties.QueueConfig(topicArn = "SOME_ARN"))
    )
  )

  @Test
  @DisplayName("Topic health is up")
  fun topicHealthIsUp() {
    Mockito.`when`(awsPrisonEventsSnsClient.getTopicAttributes("SOME_ARN")).thenReturn(GetTopicAttributesResult())
    val health = topicHealth.health()
    Assertions.assertThat(health.status).isEqualTo(Status.UP)
    Assertions.assertThat(health.details).containsEntry("arn", "SOME_ARN")
  }

  @Test
  @DisplayName("Topic health is down")
  fun topicHealthIsDown() {
    Mockito.`when`(awsPrisonEventsSnsClient.getTopicAttributes("SOME_ARN")).thenThrow(RuntimeException())
    val health = topicHealth.health()
    Assertions.assertThat(health.status).isEqualTo(Status.DOWN)
    Assertions.assertThat(health.details).containsEntry("arn", "SOME_ARN")
  }
}
