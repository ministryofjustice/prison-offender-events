package uk.gov.justice.hmpps.offenderevents.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "hmpps.sqs")
data class SqsConfigProperties(
  val region: String,
  val provider: String,
  val localstackUrl: String = "",
  val queues: Map<String, QueueConfig>,
) {
  data class QueueConfig(
    val topicArn: String = "",
    val topicAccessKeyId: String = "",
    val topicSecretAccessKey: String = "",
    val topicName: String = "",
    val queueName: String = "",
    val queueAccessKeyId: String = "",
    val queueSecretAccessKey: String = "",
    val dlqName: String = "",
    val dlqAccessKeyId: String = "",
    val dlqSecretAccessKey: String = "",
  )
}

fun SqsConfigProperties.prisonEventQueue() = queues["prisonEventQueue"] ?: throw MissingQueueException("prisonEventQueue has not been loaded from configuration properties")
fun SqsConfigProperties.hmppsDomainEventQueue() = queues["hmppsDomainEventQueue"] ?: throw MissingQueueException("hmppsDomainEventQueue has not been loaded from configuration properties")
class MissingQueueException(message: String) : RuntimeException(message)
