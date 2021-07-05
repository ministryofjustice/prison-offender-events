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
  val topics: Map<String, TopicConfig>,
) {
  data class QueueConfig(
    val queueName: String = "",
    val queueAccessKeyId: String = "",
    val queueSecretAccessKey: String = "",
    val dlqName: String = "",
    val dlqAccessKeyId: String = "",
    val dlqSecretAccessKey: String = "",
  )
  data class TopicConfig(
    val topicArn: String = "",
    val topicAccessKeyId: String = "",
    val topicSecretAccessKey: String = "",
  ) {
    val topicName
      get() = if (topicArn.startsWith(LOCALSTACK_ARN_PREFIX)) topicArn.removePrefix(LOCALSTACK_ARN_PREFIX) else "We only provide a topic name for localstack"
  }
}

fun SqsConfigProperties.prisonEventQueue() = queues["prisonEventQueue"] ?: throw MissingQueueException("prisonEventQueue has not been loaded from configuration properties")
fun SqsConfigProperties.prisonEventTopic() = topics["prisonEventTopic"] ?: throw MissingTopicException("prisonEventTopic has not been loaded from configuration properties")
fun SqsConfigProperties.hmppsDomainTopic() = topics["hmppsDomainTopic"] ?: throw MissingTopicException("hmppsDomainTopic has not been loaded from configuration properties")

fun SqsConfigProperties.prisonEventTestQueue() = queues["prisonEventTestQueue"] ?: throw MissingQueueException("prisonEventTestQueue has not been loaded from configuration properties")
fun SqsConfigProperties.hmppsDomainEventTestQueue() = queues["hmppsDomainEventTestQueue"] ?: throw MissingQueueException("hmppsDomainEventTestQueue has not been loaded from configuration properties")

class MissingQueueException(message: String) : RuntimeException(message)
class MissingTopicException(message: String) : RuntimeException(message)
