package uk.gov.justice.hmpps.offenderevents.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

const val LOCALSTACK_ARN_PREFIX = "arn:aws:sns:eu-west-2:000000000000:"

@ConstructorBinding
@ConfigurationProperties(prefix = "hmpps.sqs")
data class SqsConfigProperties(
  val region: String,
  val provider: String,
  val localstackUrl: String = "",
  val queues: Map<String, QueueConfig>,
) {
  data class QueueConfig(
    val topicAccessKeyId: String = "",
    val topicSecretAccessKey: String = "",
    val topicArn: String = "",
    val queueName: String = "",
    val queueAccessKeyId: String = "",
    val queueSecretAccessKey: String = "",
    val dlqName: String = "",
    val dlqAccessKeyId: String = "",
    val dlqSecretAccessKey: String = "",
  ) {
    val topicName
      get() = if (topicArn.startsWith(LOCALSTACK_ARN_PREFIX)) topicArn.removePrefix(LOCALSTACK_ARN_PREFIX) else "We only provide a topic name for localstack"
  }
}

fun SqsConfigProperties.prisonEventQueue() = queues["prisonEventQueue"] ?: throw MissingQueueException("prisonEventQueue has not been loaded from configuration properties")
fun SqsConfigProperties.hmppsDomainEventQueue() = queues["hmppsDomainEventQueue"] ?: throw MissingQueueException("hmppsDomainEventQueue has not been loaded from configuration properties")
class MissingQueueException(message: String) : RuntimeException(message)
