package uk.gov.justice.hmpps.offenderevents.services

import com.amazonaws.services.sns.AmazonSNSAsync
import com.amazonaws.services.sns.model.PublishRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import uk.gov.justice.hmpps.offenderevents.config.SqsConfigProperties
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent

@RunWith(MockitoJUnitRunner::class)
class PrisonEventsEmitterTest {
  @Mock
  private val awsPrisonEventsSnsClient: AmazonSNSAsync? = null
  private val objectMapper = ObjectMapper()
  private var service: PrisonEventsEmitter? = null

  @Mock
  private val telemetryClient: TelemetryClient? = null

  @Captor
  private val telemetryAttributesCaptor: ArgumentCaptor<Map<String, String>>? = null
  @Before
  fun setup() {
    service = PrisonEventsEmitter(
      awsPrisonEventsSnsClient,
      SqsConfigProperties(
        "", "", topics = mapOf("prisonEventTopic" to SqsConfigProperties.TopicConfig(topicArn = "topicARN")),
        queues = mapOf("prisonEventQueue" to SqsConfigProperties.QueueConfig(queueName = "queueName"))
      ),
      objectMapper, telemetryClient
    )
  }

  @Test
  fun testSendEvent() {
    val payload = OffenderEvent.builder()
      .eventType("my-event-type")
      .alertCode("alert-code")
      .bookingId(12345L)
      .build()
    service!!.sendEvent(payload)
    val argumentCaptor = ArgumentCaptor.forClass(
      PublishRequest::class.java
    )
    Mockito.verify(awsPrisonEventsSnsClient)!!.publish(argumentCaptor.capture())
    val request = argumentCaptor.value
    Assertions.assertThat(request.messageAttributes["eventType"]!!.stringValue).isEqualTo("my-event-type")
    Assertions.assertThat(request.messageAttributes["code"]!!.stringValue).isEqualTo("alert-code")
    Assertions.assertThat(request).extracting("message")
      .isEqualTo("{\"eventType\":\"my-event-type\",\"bookingId\":12345,\"alertCode\":\"alert-code\"}")
    Mockito.verify(telemetryClient)!!.trackEvent(
      ArgumentMatchers.eq("my-event-type"),
      telemetryAttributesCaptor!!.capture(),
      ArgumentMatchers.isNull()
    )
    Assertions.assertThat(telemetryAttributesCaptor.value).containsAllEntriesOf(
      java.util.Map.of(
        "eventType",
        "my-event-type",
        "bookingId",
        "12345",
        "alertCode",
        "alert-code"
      )
    )
  }
}
