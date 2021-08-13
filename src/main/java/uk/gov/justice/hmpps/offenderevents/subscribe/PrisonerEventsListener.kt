package uk.gov.justice.hmpps.offenderevents.subscribe

import lombok.extern.slf4j.Slf4j
import uk.gov.justice.hmpps.offenderevents.services.HMPPSDomainEventsEmitter
import org.springframework.jms.annotation.JmsListener
import kotlin.Throws
import com.amazon.sqs.javamessaging.message.SQSTextMessage
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import uk.gov.justice.hmpps.offenderevents.subscribe.PrisonerEventsListener
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent
import java.time.Duration

@Service
@Slf4j
class PrisonerEventsListener(
    private val objectMapper: ObjectMapper,
    private val eventsEmitter: HMPPSDomainEventsEmitter,
    private val client: AmazonSQS,
    @param:Value("\${application.listener.totalDelayDuration}") private val totalDelay: Duration,
    @param:Value("\${application.listener.delayDuration}") private val delay: Duration
) {
    @JmsListener(destination = "prisoneventqueue", containerFactory = "hmppsQueueContainerFactoryProxy")
    @Throws(
        JsonProcessingException::class
    )
    fun onPrisonerEvent(message: String?, rawMessage: SQSTextMessage) {
        val sqsMessage = objectMapper.readValue(message, SQSMessage::class.java)
        val publishedAt = OffsetDateTime.parse(sqsMessage.MessageAttributes().publishedAt().Value())
        if (publishedAt.isBefore(OffsetDateTime.now().minus(totalDelay))) {
            PrisonerEventsListener.log.debug("Received message {} published at {}", sqsMessage.MessageId(), publishedAt)
            val event: OffenderEvent =
                objectMapper.readValue<OffenderEvent>(sqsMessage.Message(), OffenderEvent::class.java)
            eventsEmitter.convertAndSendWhenSignificant(event)
        } else {
            client.sendMessage(
                SendMessageRequest()
                    .withQueueUrl(rawMessage.queueUrl)
                    .withMessageBody(message)
                    .withDelaySeconds(delay.toSeconds().toInt())
            )
        }
    }
}

internal class SQSMessage
internal class PublishedAt
internal class MessageAttributes 