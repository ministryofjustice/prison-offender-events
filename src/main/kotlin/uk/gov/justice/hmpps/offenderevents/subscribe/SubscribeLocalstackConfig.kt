package uk.gov.justice.hmpps.offenderevents.subscribe

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.AmazonSNSAsync
import com.amazonaws.services.sns.AmazonSNSAsyncClientBuilder
import com.amazonaws.services.sns.model.SubscribeRequest
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.CreateQueueRequest
import com.amazonaws.services.sqs.model.QueueAttributeName
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import uk.gov.justice.hmpps.offenderevents.config.MissingQueueException
import uk.gov.justice.hmpps.offenderevents.config.SqsConfigProperties
import uk.gov.justice.hmpps.offenderevents.config.hmppsEventTopic
import uk.gov.justice.hmpps.offenderevents.config.prisonEventQueue
import uk.gov.justice.hmpps.offenderevents.config.prisonEventTopic
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Configuration
@ConditionalOnProperty(name = ["hmpps.sqs.provider"], havingValue = "localstack")
class SubscribeLocalstackConfig(private val hmppsQueueService: HmppsQueueService) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Bean
  fun awsSqsClient(
    sqsConfigProperties: SqsConfigProperties,
    awsSqsDlqClient: AmazonSQS,
    @Qualifier("awsPrisonEventsSnsClient") awsPrisonEventsSnsClient: AmazonSNS
  ): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(localstackUrl, region)
        .also { sqsClient -> createQueue(sqsClient, awsSqsDlqClient, prisonEventQueue().queueName, prisonEventQueue().dlqName) }
        .also { log.info("Created localstack sqs client for queue ${prisonEventQueue().queueName}") }
        .also {
          subscribeToTopic(
            awsPrisonEventsSnsClient, localstackUrl, region, prisonEventTopic().topicName, prisonEventQueue().queueName,
            mapOf("FilterPolicy" to """{"eventType":[ "OFFENDER_MOVEMENT-RECEPTION", "OFFENDER_MOVEMENT-DISCHARGE"] }""")
          )
        }
        .also { log.info("Queue ${prisonEventQueue().queueName} has subscribed to dps topic ${prisonEventTopic().topicName}") }
        .also { hmppsQueueService.registerHmppsQueue("prisonEventQueue", it, prisonEventQueue().queueName, awsSqsDlqClient, prisonEventQueue().dlqName) }
    }

  @Bean
  fun awsSqsDlqClient(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(localstackUrl, region)
        .also { dlqSqsClient -> dlqSqsClient.createQueue(prisonEventQueue().dlqName) }
        .also { log.info("Created localstack dlq sqs client for dlq ${prisonEventQueue().dlqName}") }
    }

  @Bean
  @Profile("test")
  fun testSqsClient(
    sqsConfigProperties: SqsConfigProperties,
    testSqsDlqClient: AmazonSQS,
    @Qualifier("awsPrisonEventsSnsClient") awsPrisonEventsSnsClient: AmazonSNS
  ): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(localstackUrl, region)
        .also { sqsClient -> createQueue(sqsClient, testSqsDlqClient, prisonEventTestQueue().queueName, prisonEventTestQueue().dlqName) }
        .also { log.info("Created TEST localstack sqs client for queue ${prisonEventTestQueue().queueName}") }
        .also {
          subscribeToTopic(
            awsPrisonEventsSnsClient, localstackUrl, region, prisonEventTopic().topicName, prisonEventTestQueue().queueName,
            mapOf("FilterPolicy" to """{"eventType":[ "OFFENDER_MOVEMENT-RECEPTION", "OFFENDER_MOVEMENT-DISCHARGE"] }""")
          )
        }
        .also { log.info("TEST Queue ${prisonEventTestQueue().queueName} has subscribed to dps topic ${prisonEventTopic().topicName}") }
    }

  @Bean
  @Profile("test")
  fun testSqsDlqClient(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(localstackUrl, region)
        .also { dlqSqsClient -> dlqSqsClient.createQueue(prisonEventTestQueue().dlqName) }
        .also { log.info("Created TEST localstack dlq sqs client for dlq ${prisonEventTestQueue().dlqName}") }
    }

  @Bean
  @Profile("test")
  fun testHmppsSqsClient(
    sqsConfigProperties: SqsConfigProperties,
    testHmppsSqsDlqClient: AmazonSQS,
    @Qualifier("awsHMPPSEventsSnsClient") awsHMPPSEventsSnsClient: AmazonSNS,
  ): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(localstackUrl, region)
        .also { sqsClient -> createQueue(sqsClient, testHmppsSqsDlqClient, hmppsEventTestQueue().queueName, hmppsEventTestQueue().dlqName) }
        .also { log.info("Created TEST localstack sqs client for queue ${hmppsEventTestQueue().queueName}") }
        .also {
          subscribeToTopic(
            awsHMPPSEventsSnsClient, localstackUrl, region, hmppsEventTopic().topicName, hmppsEventTestQueue().queueName,
            mapOf()
          )
        }
        .also { log.info("TEST Queue ${hmppsEventTestQueue().queueName} has subscribed to hmpps topic ${hmppsEventTopic().topicName}") }
    }

  @Bean
  @Profile("test")
  fun testHmppsSqsDlqClient(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(localstackUrl, region)
        .also { dlqSqsClient -> dlqSqsClient.createQueue(hmppsEventTestQueue().dlqName) }
        .also { log.info("Created TEST localstack dlq sqs client for dlq ${hmppsEventTestQueue().dlqName}") }
    }

  private fun subscribeToTopic(
    awsSnsClient: AmazonSNS,
    localstackUrl: String,
    region: String,
    topicName: String,
    queueName: String,
    attributes: Map<String, String>
  ) =
    awsSnsClient.subscribe(
      SubscribeRequest()
        .withTopicArn(localstackTopicArn(region, topicName))
        .withProtocol("sqs")
        .withEndpoint("$localstackUrl/queue/$queueName")
        .withAttributes(attributes)
    )

  @Bean
  @Primary
  fun awsPrisonEventsSnsClient(sqsConfigProperties: SqsConfigProperties): AmazonSNSAsync =
    with(sqsConfigProperties) {
      localstackAmazonSNS(localstackUrl, region)
        .also { snsClient -> snsClient.createTopic(prisonEventTopic().topicName) }
        .also { log.info("Created localstack sns topic with name ${prisonEventTopic().topicName}") }
    }

  @Bean
  fun awsHMPPSEventsSnsClient(sqsConfigProperties: SqsConfigProperties): AmazonSNSAsync =
    with(sqsConfigProperties) {
      localstackAmazonSNS(sqsConfigProperties.localstackUrl, sqsConfigProperties.region)
        .also { snsClient -> snsClient.createTopic(hmppsEventTopic().topicName) }
        .also { log.info("Created localstack sns topic with name ${hmppsEventTopic().topicName}") }
    }

  private fun localstackTopicArn(region: String, topicName: String) = "arn:aws:sns:$region:000000000000:$topicName"

  private fun localstackAmazonSNS(localstackUrl: String, region: String) =
    AmazonSNSAsyncClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(localstackUrl, region))
      .build()

  private fun localstackAmazonSQS(localstackUrl: String, region: String) =
    AmazonSQSClientBuilder.standard()
      .withEndpointConfiguration(AwsClientBuilder.EndpointConfiguration(localstackUrl, region))
      .withCredentials(AWSStaticCredentialsProvider(AnonymousAWSCredentials()))
      .build()

  private fun createQueue(
    queueSqsClient: AmazonSQS,
    dlqSqsClient: AmazonSQS,
    queueName: String,
    dlqName: String,
  ) =
    dlqSqsClient.getQueueUrl(dlqName).queueUrl
      .let { dlqQueueUrl -> dlqSqsClient.getQueueAttributes(dlqQueueUrl, listOf(QueueAttributeName.QueueArn.toString())).attributes["QueueArn"]!! }
      .also { queueArn ->
        queueSqsClient.createQueue(
          CreateQueueRequest(queueName).withAttributes(
            mapOf(
              QueueAttributeName.RedrivePolicy.toString() to
                """{"deadLetterTargetArn":"$queueArn","maxReceiveCount":"5"}"""
            )
          )
        )
      }
}

fun SqsConfigProperties.prisonEventTestQueue() = queues["prisonEventTestQueue"] ?: throw MissingQueueException("prisonEventTestQueue has not been loaded from configuration properties")
fun SqsConfigProperties.hmppsEventTestQueue() = queues["hmppsEventTestQueue"] ?: throw MissingQueueException("hmppsEventTestQueue has not been loaded from configuration properties")
