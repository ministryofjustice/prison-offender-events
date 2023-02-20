package uk.gov.justice.hmpps.offenderevents.resource

import org.awaitility.kotlin.await
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

abstract class QueueListenerIntegrationTest : IntegrationTestBase() {

  @Autowired
  lateinit var hmppsQueueService: HmppsQueueService

  internal val prisonEventQueue by lazy { hmppsQueueService.findByQueueId("prisoneventqueue") as HmppsQueue }
  internal val prisonEventTestQueue by lazy { hmppsQueueService.findByQueueId("prisoneventtestqueue") as HmppsQueue }
  internal val hmppsEventTestQueue by lazy { hmppsQueueService.findByQueueId("hmppseventtestqueue") as HmppsQueue }
  internal val prisonEventTopic by lazy { hmppsQueueService.findByTopicId("prisoneventtopic") as HmppsTopic }

  internal val prisonEventQueueSqsClient by lazy { prisonEventQueue.sqsClient }
  internal val prisonEventQueueName by lazy { prisonEventQueue.queueName }
  internal val prisonEventQueueUrl by lazy { prisonEventQueue.queueUrl }

  internal val prisonEventSqsDlqClient by lazy { prisonEventQueue.sqsDlqClient as SqsAsyncClient }
  internal val prisonEventDlqName by lazy { prisonEventQueue.dlqName as String }
  internal val prisonEventDlqUrl by lazy { prisonEventQueue.dlqUrl as String }

  protected val prisonEventTestQueueSqsClient by lazy { prisonEventTestQueue.sqsClient }
  protected val prisonEventTestQueueUrl by lazy { prisonEventTestQueue.queueUrl }

  internal val hmppsEventTestQueueSqsClient by lazy { hmppsEventTestQueue.sqsClient }
  protected val hmppsEventTestQueueUrl: String by lazy { hmppsEventTestQueue.queueUrl }

  protected val prisonEventTopicSnsClient by lazy { prisonEventTopic.snsClient }
  protected val prisonEventTopicArn by lazy { prisonEventTopic.arn }

  fun purgeQueues() {
    prisonEventQueueSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(prisonEventQueueUrl).build()).get()
    await.until { getNumberOfMessagesCurrentlyOnPrisonEventQueue() == 0 }
    prisonEventQueueSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(prisonEventDlqUrl).build()).get()
    await.until { getNumberOfMessagesCurrentlyOnPrisonEventDlq() == 0 }
    prisonEventTestQueueSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(prisonEventTestQueueUrl).build()).get()
    await.until { getNumberOfMessagesCurrentlyOnPrisonEventTestQueue() == 0 }
    hmppsEventTestQueueSqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(hmppsEventTestQueueUrl).build()).get()
    await.until { getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue() == 0 }
  }

  fun getNumberOfMessagesCurrentlyOnPrisonEventQueue(): Int = prisonEventQueueSqsClient.countAllMessagesOnQueue(prisonEventQueueUrl).get()
  fun getNumberOfMessagesCurrentlyOnPrisonEventDlq(): Int = prisonEventSqsDlqClient.countAllMessagesOnQueue(prisonEventDlqUrl).get()
  fun getNumberOfMessagesCurrentlyOnPrisonEventTestQueue(): Int = prisonEventTestQueueSqsClient.countAllMessagesOnQueue(prisonEventTestQueueUrl).get()
  fun getNumberOfMessagesCurrentlyOnHMPPSEventTestQueue(): Int = hmppsEventTestQueueSqsClient.countAllMessagesOnQueue(hmppsEventTestQueueUrl).get()
}
