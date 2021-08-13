package uk.gov.justice.hmpps.offenderevents.resource

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import org.awaitility.kotlin.await
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

abstract class QueueListenerIntegrationTest : IntegrationTestBase() {

  @Autowired
  lateinit var hmppsQueueService: HmppsQueueService

  internal val prisonEventQueue by lazy { hmppsQueueService.findByQueueId("prisoneventqueue") as HmppsQueue }
  internal val prisonEventTestQueue by lazy { hmppsQueueService.findByQueueId("prisoneventtestqueue") as HmppsQueue }
  internal val hmppsEventTestQueue by lazy { hmppsQueueService.findByQueueId("hmppseventtestqueue") as HmppsQueue }

  internal val prisonEventQueueSqsClient by lazy { prisonEventQueue.sqsClient }
  internal val prisonEventQueueName by lazy { prisonEventQueue.queueName }
  internal val prisonEventQueueUrl by lazy { prisonEventQueue.queueUrl }

  internal val prisonEventSqsDlqClient by lazy { prisonEventQueue.sqsDlqClient as AmazonSQS }
  internal val prisonEventDlqName by lazy { prisonEventQueue.dlqName as String }
  internal val prisonEventDlqUrl by lazy { prisonEventQueue.dlqUrl as String }

  protected val prisonEventTestQueueSqsClient by lazy { prisonEventTestQueue.sqsClient }
  protected val prisonEventTestQueueUrl by lazy { prisonEventTestQueue.queueUrl }

  internal val hmppsEventTestQueueSqsClient by lazy { hmppsEventTestQueue.sqsClient }
  protected val hmppsEventTestQueueUrl: String by lazy { hmppsEventTestQueue.queueUrl }

  fun purgeQueues() {
    prisonEventQueueSqsClient.purgeQueue(PurgeQueueRequest(prisonEventQueueUrl))
    await.until { getNumberOfMessagesCurrentlyOnPrisonEventQueue() == 0 }
    prisonEventQueueSqsClient.purgeQueue(PurgeQueueRequest(prisonEventDlqUrl))
    await.until { getNumberOfMessagesCurrentlyOnPrisonEventDlq() == 0 }
    prisonEventTestQueueSqsClient.purgeQueue(PurgeQueueRequest(prisonEventTestQueueUrl))
    await.until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 0 }
    hmppsEventTestQueueSqsClient.purgeQueue(PurgeQueueRequest(hmppsEventTestQueueUrl))
    await.until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 0 }
  }

  fun getNumberOfMessagesCurrentlyOnPrisonEventQueue(): Int = prisonEventQueueSqsClient.numMessages(prisonEventQueueUrl)
  fun getNumberOfMessagesCurrentlyOnPrisonEventDlq(): Int = prisonEventSqsDlqClient.numMessages(prisonEventDlqUrl)
  fun getNumberOfMessagesCurrentlyOnPrisonEventTestQueue(): Int = prisonEventTestQueueSqsClient.numMessages(prisonEventTestQueueUrl)
  fun getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue(): Int = hmppsEventTestQueueSqsClient.numMessages(hmppsEventTestQueueUrl)
}

fun AmazonSQS.numMessages(url: String): Int {
  val queueAttributes = getQueueAttributes(url, listOf("ApproximateNumberOfMessages"))
  return queueAttributes.attributes["ApproximateNumberOfMessages"]!!.toInt()
}
