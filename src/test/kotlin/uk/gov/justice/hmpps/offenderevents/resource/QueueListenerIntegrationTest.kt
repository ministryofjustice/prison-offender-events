package uk.gov.justice.hmpps.offenderevents.resource

import com.amazonaws.services.sqs.AmazonSQS
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.mock.mockito.SpyBean
import uk.gov.justice.hmpps.offenderevents.config.SqsConfigProperties
import uk.gov.justice.hmpps.offenderevents.config.hmppsDomainEventQueue
import uk.gov.justice.hmpps.offenderevents.config.prisonEventQueue

abstract class QueueListenerIntegrationTest : IntegrationTestBase() {

  @SpyBean
  @Qualifier("awsSqsClient")
  protected lateinit var awsSqsClient: AmazonSQS

  @SpyBean
  @Qualifier("awsSqsDlqClient")
  protected lateinit var awsSqsDlqClient: AmazonSQS

  @SpyBean
  @Qualifier("awsHmppsSqsClient")
  protected lateinit var awsHmppsSqsClient: AmazonSQS

  @SpyBean
  @Qualifier("awsHmppsSqsDlqClient")
  protected lateinit var awsHmppsSqsDlqClient: AmazonSQS

  @Autowired
  protected lateinit var sqsConfigProperties: SqsConfigProperties

  fun getNumberOfMessagesCurrentlyOnQueue(): Int = awsSqsClient.numMessages("prisonEventsQueue", queueUrl)
  val queueName: String by lazy { sqsConfigProperties.prisonEventQueue().queueName }
  val queueUrl: String by lazy { awsSqsClient.getQueueUrl(queueName).queueUrl }

  fun getNumberOfMessagesCurrentlyOnDlq(): Int = awsSqsDlqClient.numMessages("prisonEventsDlq", dlqUrl)

  val dlqName: String by lazy { sqsConfigProperties.prisonEventQueue().dlqName }
  val dlqUrl: String by lazy { awsSqsDlqClient.getQueueUrl(dlqName).queueUrl }

  fun getNumberOfMessagesCurrentlyOnHMPPSQueue(): Int = awsHmppsSqsClient.numMessages("hmppsQueueUrl", hmppsQueueUrl)
  val hmppsQueueName: String by lazy { sqsConfigProperties.hmppsDomainEventQueue().queueName }
  val hmppsQueueUrl: String by lazy { awsHmppsSqsClient.getQueueUrl(hmppsQueueName).queueUrl }

  fun getNumberOfMessagesCurrentlyOnHMPPSDlq(): Int = awsHmppsSqsDlqClient.numMessages("hmppsDlqlUrl", hmppsDlqlUrl)
  val hmppsDlqName: String by lazy { sqsConfigProperties.hmppsDomainEventQueue().dlqName }
  val hmppsDlqlUrl: String by lazy { awsHmppsSqsDlqClient.getQueueUrl(hmppsDlqName).queueUrl }
}

fun AmazonSQS.numMessages(readableName: String, url: String): Int {
  val queueAttributes = getQueueAttributes(url, listOf("ApproximateNumberOfMessages"))

  // TODO REMOVE
  val numMessages = queueAttributes.attributes["ApproximateNumberOfMessages"]!!.toInt()
  println("Messages on $readableName $url  $numMessages")

  return queueAttributes.attributes["ApproximateNumberOfMessages"]!!.toInt()
}
