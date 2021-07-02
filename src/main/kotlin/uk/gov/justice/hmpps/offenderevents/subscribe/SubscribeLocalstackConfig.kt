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
import uk.gov.justice.hmpps.offenderevents.config.SqsConfigProperties
import uk.gov.justice.hmpps.offenderevents.config.hmppsDomainEventQueue
import uk.gov.justice.hmpps.offenderevents.config.prisonEventQueue
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
            awsPrisonEventsSnsClient, localstackUrl, region, prisonEventQueue().topicName, prisonEventQueue().queueName,
            mapOf("FilterPolicy" to """{"eventType":[ "OFFENDER_MOVEMENT-RECEPTION", "OFFENDER_MOVEMENT-DISCHARGE"] }""")
          )
        }
        .also { log.info("Queue ${prisonEventQueue().queueName} has subscribed to dps topic ${prisonEventQueue().topicName}") }
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
  fun awsHmppsSqsClient(
    sqsConfigProperties: SqsConfigProperties,
    awsHmppsSqsDlqClient: AmazonSQS,
    @Qualifier("awsHMPPSEventsSnsClient") awsHMPPSEventsSnsClient: AmazonSNS,
  ): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(localstackUrl, region)
        .also { sqsClient -> createQueue(sqsClient, awsHmppsSqsDlqClient, hmppsDomainEventQueue().queueName, hmppsDomainEventQueue().dlqName) }
        .also { log.info("Created localstack sqs client for queue ${hmppsDomainEventQueue().queueName}") }
        .also {
          subscribeToTopic(
            awsHMPPSEventsSnsClient, localstackUrl, region, hmppsDomainEventQueue().topicName, hmppsDomainEventQueue().queueName,
            mapOf()
          )
        }
        .also { log.info("Queue ${hmppsDomainEventQueue().queueName} has subscribed to hmpps topic ${hmppsDomainEventQueue().topicName}") }
        .also { hmppsQueueService.registerHmppsQueue("hmppsDomainEventQueue", it, hmppsDomainEventQueue().queueName, awsHmppsSqsDlqClient, hmppsDomainEventQueue().dlqName) }
    }

  @Bean
  fun awsHmppsSqsDlqClient(sqsConfigProperties: SqsConfigProperties): AmazonSQS =
    with(sqsConfigProperties) {
      localstackAmazonSQS(localstackUrl, region)
        .also { dlqSqsClient -> dlqSqsClient.createQueue(hmppsDomainEventQueue().dlqName) }
        .also { log.info("Created localstack dlq sqs client for dlq ${hmppsDomainEventQueue().dlqName}") }
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
        .also { snsClient -> snsClient.createTopic(prisonEventQueue().topicName) }
        .also { log.info("Created localstack sns topic with name ${prisonEventQueue().topicName}") }
    }

  @Bean
  fun awsHMPPSEventsSnsClient(sqsConfigProperties: SqsConfigProperties): AmazonSNSAsync =
    with(sqsConfigProperties) {
      localstackAmazonSNS(sqsConfigProperties.localstackUrl, sqsConfigProperties.region)
        .also { snsClient -> snsClient.createTopic(hmppsDomainEventQueue().topicName) }
        .also { log.info("Created localstack sns topic with name ${hmppsDomainEventQueue().topicName}") }
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
