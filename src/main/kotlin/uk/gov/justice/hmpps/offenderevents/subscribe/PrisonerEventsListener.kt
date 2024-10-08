@file:Suppress("PropertyName")

package uk.gov.justice.hmpps.offenderevents.subscribe

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.awspring.cloud.sqs.listener.QueueAttributes
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.hmpps.offenderevents.services.HMPPSDomainEventsEmitter
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.eventTypeMessageAttributes
import java.time.Duration
import java.time.OffsetDateTime

@Service
class PrisonerEventsListener(
  private val objectMapper: ObjectMapper,
  private val eventsEmitter: HMPPSDomainEventsEmitter,
  private val hmppsQueueService: HmppsQueueService,
  @Value("\${application.listener.totalDelayDuration}") private val totalDelay: Duration,
  @Value("\${application.listener.delayDuration}") private val delay: Duration,
) {
  @SqsListener(queueNames = ["prisoneventqueue"], factory = "hmppsQueueContainerFactoryProxy")
  @Throws(JsonProcessingException::class)
  fun onPrisonerEvent(message: String?, attributes: QueueAttributes) {
    val sqsMessage: SQSMessage = objectMapper.readValue(message, SQSMessage::class.java)
    val publishedAt = OffsetDateTime.parse(sqsMessage.MessageAttributes.publishedAt.Value)
    val eventType = sqsMessage.MessageAttributes.eventType.Value
    if (!DELAYED_EVENT_TYPES.contains(eventType) || publishedAt.isBefore(OffsetDateTime.now().minus(totalDelay))) {
      log.debug(
        "Received message {} type {} published at {}",
        sqsMessage.MessageId,
        eventType,
        publishedAt,
      )
      eventsEmitter.convertAndSendWhenSignificant(eventType, sqsMessage.Message)
    } else {
      hmppsQueueService.findByQueueId("prisoneventqueue")!!.sqsClient
        .sendMessage(
          SendMessageRequest.builder()
            .queueUrl(attributes.queueUrl)
            .messageBody(message)
            .eventTypeMessageAttributes(eventType)
            .delaySeconds(delay.toSeconds().toInt())
            .build(),
        )
    }
  }

  private companion object {
    private val DELAYED_EVENT_TYPES = listOf("OFFENDER_MOVEMENT-RECEPTION", "OFFENDER_MOVEMENT-DISCHARGE")
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

internal data class SQSMessage(val Message: String, val MessageId: String, val MessageAttributes: MessageAttributes)
internal data class MessageAttributes(val publishedAt: TypeValuePair, val eventType: TypeValuePair)
internal data class TypeValuePair(val Value: String, val Type: String)
