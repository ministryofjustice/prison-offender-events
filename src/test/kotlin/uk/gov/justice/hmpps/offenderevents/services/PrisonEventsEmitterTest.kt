package uk.gov.justice.hmpps.offenderevents.services

import com.amazonaws.services.sns.AmazonSNSAsync
import com.amazonaws.services.sns.model.PublishRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit.SECONDS

@RunWith(MockitoJUnitRunner::class)
class PrisonEventsEmitterTest {
  private val objectMapper = ObjectMapper()
  private lateinit var service: PrisonEventsEmitter

  @Mock
  private lateinit var telemetryClient: TelemetryClient

  @Captor
  private lateinit var telemetryAttributesCaptor: ArgumentCaptor<Map<String, String>>

  @Captor
  private lateinit var publishRequestCaptor: ArgumentCaptor<PublishRequest>

  private val hmppsQueueService = mock<HmppsQueueService>()
  private val prisonEventSnsClient = mock<AmazonSNSAsync>()

  @Before
  fun setup() {
    whenever(hmppsQueueService.findByTopicId("prisoneventtopic"))
      .thenReturn(HmppsTopic("prisoneventtopic", "topicARN", prisonEventSnsClient))

    service = PrisonEventsEmitter(hmppsQueueService, objectMapper, telemetryClient)
  }

  @Test
  fun `will add payload as message`() {
    val payload = OffenderEvent.builder()
      .eventType("my-event-type")
      .alertCode("alert-code")
      .bookingId(12345L)
      .build()
    service.sendEvent(payload)
    verify(prisonEventSnsClient).publishAsync(publishRequestCaptor.capture())
    val request = publishRequestCaptor.value

    assertThat(request).extracting("message")
      .isEqualTo("{\"eventType\":\"my-event-type\",\"bookingId\":12345,\"alertCode\":\"alert-code\"}")
  }

  @Test
  fun `will add telemetry event`() {
    service.sendEvent(
      OffenderEvent.builder()
        .eventType("my-event-type")
        .alertCode("alert-code")
        .bookingId(12345L)
        .build()
    )

    verify(telemetryClient).trackEvent(
      ArgumentMatchers.eq("my-event-type"),
      telemetryAttributesCaptor.capture(),
      ArgumentMatchers.isNull()
    )
    assertThat(telemetryAttributesCaptor.value).containsAllEntriesOf(
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

  @Test
  fun `will add code`() {
    service.sendEvent(
      OffenderEvent.builder()
        .eventType("my-event-type")
        .alertCode("alert-code")
        .bookingId(12345L)
        .build()
    )

    verify(prisonEventSnsClient).publishAsync(publishRequestCaptor.capture())
    val request = publishRequestCaptor.value

    assertThat(request.messageAttributes["code"]).satisfies {
      assertThat(it?.stringValue).isEqualTo("alert-code")
    }
  }

  @Test
  fun `code is present only for some events`() {
    service.sendEvent(
      OffenderEvent.builder()
        .eventType("my-event-type")
        .bookingId(12345L)
        .build()
    )

    verify(prisonEventSnsClient).publishAsync(publishRequestCaptor.capture())
    val request = publishRequestCaptor.value

    assertThat(request.messageAttributes["code"]).isNull()
  }

  @Test
  fun `will add event type`() {
    service.sendEvent(
      OffenderEvent.builder()
        .eventType("my-event-type")
        .alertCode("alert-code")
        .bookingId(12345L)
        .build()
    )

    verify(prisonEventSnsClient).publishAsync(publishRequestCaptor.capture())
    val request = publishRequestCaptor.value

    assertThat(request.messageAttributes["eventType"]).satisfies {
      assertThat(it?.stringValue).isEqualTo("my-event-type")
    }
  }

  @Test
  fun `will add the date time event is published`() {
    service.sendEvent(
      OffenderEvent.builder()
        .eventType("my-event-type")
        .alertCode("alert-code")
        .bookingId(12345L)
        .build()
    )

    verify(prisonEventSnsClient).publishAsync(publishRequestCaptor.capture())
    val request = publishRequestCaptor.value

    assertThat(request.messageAttributes["publishedAt"]).isNotNull.satisfies {
      assertThat(OffsetDateTime.parse(it?.stringValue).toLocalDateTime())
        .isCloseTo(LocalDateTime.now(), Assertions.within(10, SECONDS))
    }
  }
}
