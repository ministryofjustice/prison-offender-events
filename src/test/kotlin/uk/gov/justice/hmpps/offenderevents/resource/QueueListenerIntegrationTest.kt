package uk.gov.justice.hmpps.offenderevents.resource

import com.amazonaws.services.sqs.AmazonSQS
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.mock.mockito.SpyBean
import uk.gov.justice.hmpps.offenderevents.config.SqsConfigProperties
import uk.gov.justice.hmpps.offenderevents.config.hmppsDomainEventTestQueue
import uk.gov.justice.hmpps.offenderevents.config.prisonEventQueue
import uk.gov.justice.hmpps.offenderevents.config.prisonEventTestQueue

abstract class QueueListenerIntegrationTest : IntegrationTestBase() {

  @SpyBean
  @Qualifier("awsSqsClient")
  protected lateinit var awsSqsClient: AmazonSQS

  @SpyBean
  @Qualifier("awsSqsDlqClient")
  protected lateinit var awsSqsDlqClient: AmazonSQS

  @SpyBean
  @Qualifier("testSqsClient")
  protected lateinit var testSqsClient: AmazonSQS

  @SpyBean
  @Qualifier("testSqsDlqClient")
  protected lateinit var testSqsDlqClient: AmazonSQS

  @SpyBean
  @Qualifier("testHmppsSqsClient")
  protected lateinit var testHmppsSqsClient: AmazonSQS

  @SpyBean
  @Qualifier("testHmppsSqsDlqClient")
  protected lateinit var testHmppsSqsDlqClient: AmazonSQS

  @Autowired
  protected lateinit var sqsConfigProperties: SqsConfigProperties

  // The SQS clients for the production prisonEventsQueue
  fun getNumberOfMessagesCurrentlyOnQueue(): Int = awsSqsClient.numMessages("prisonEventsQueueUrl", queueUrl)
  val queueName: String by lazy { sqsConfigProperties.prisonEventQueue().queueName }
  val queueUrl: String by lazy { awsSqsClient.getQueueUrl(queueName).queueUrl }
  fun getNumberOfMessagesCurrentlyOnDlq(): Int = awsSqsDlqClient.numMessages("prisonEventsDlqUrl", dlqUrl)
  val dlqName: String by lazy { sqsConfigProperties.prisonEventQueue().dlqName }
  val dlqUrl: String by lazy { awsSqsDlqClient.getQueueUrl(dlqName).queueUrl }

  // The SQS clients for the test prisonEventsTestQueue
  fun getNumberOfMessagesCurrentlyOnTestQueue(): Int = testSqsClient.numMessages("prisonEventsTestQueueUrl", testQueueUrl)
  val testQueueName: String by lazy { sqsConfigProperties.prisonEventTestQueue().queueName }
  val testQueueUrl: String by lazy { testSqsClient.getQueueUrl(testQueueName).queueUrl }
  fun getNumberOfMessagesCurrentlyOnTestDlq(): Int = testSqsDlqClient.numMessages("prisonEventsTestDlqUrl", testDlqUrl)
  val testDlqName: String by lazy { sqsConfigProperties.prisonEventTestQueue().dlqName }
  val testDlqUrl: String by lazy { testSqsDlqClient.getQueueUrl(testDlqName).queueUrl }

  // The SQS clients for the test hmppsDomainEventTestQueue
  fun getNumberOfMessagesCurrentlyOnHMPPSTestQueue(): Int = testHmppsSqsClient.numMessages("hmppsTestQueueUrl", testHmppsQueueUrl)
  val testHmppsQueueName: String by lazy { sqsConfigProperties.hmppsDomainEventTestQueue().queueName }
  val testHmppsQueueUrl: String by lazy { testHmppsSqsClient.getQueueUrl(testHmppsQueueName).queueUrl }
  fun getNumberOfMessagesCurrentlyOnHMPPSTestDlq(): Int = testHmppsSqsDlqClient.numMessages("hmppsTestDlqlUrl", testHmppsDlqlUrl)
  val testHmppsDlqName: String by lazy { sqsConfigProperties.hmppsDomainEventTestQueue().dlqName }
  val testHmppsDlqlUrl: String by lazy { testHmppsSqsDlqClient.getQueueUrl(testHmppsDlqName).queueUrl }
}

fun AmazonSQS.numMessages(readableName: String, url: String): Int {
  val queueAttributes = getQueueAttributes(url, listOf("ApproximateNumberOfMessages"))

  // TODO REMOVE
  val numMessages = queueAttributes.attributes["ApproximateNumberOfMessages"]!!.toInt()
  println("Messages on $readableName $url  $numMessages")

  return queueAttributes.attributes["ApproximateNumberOfMessages"]!!.toInt()
}
