package uk.gov.justice.hmpps.offenderevents.config

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.time.Duration

@Import(JwtAuthorisationHelper::class, ClientTrackingInterceptor::class, ClientTrackingConfiguration::class)
@ContextConfiguration(initializers = [ConfigDataApplicationContextInitializer::class])
@ActiveProfiles("test")
@ExtendWith(SpringExtension::class)
class ClientTrackingConfigurationTest {
  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private lateinit var clientTrackingInterceptor: ClientTrackingInterceptor

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  private lateinit var jwtAuthHelper: JwtAuthorisationHelper
  private val res = MockHttpServletResponse()
  private val req = MockHttpServletRequest()

  private val tracer: Tracer = otelTesting.openTelemetry.getTracer("test")

  @Test
  fun shouldAddClientIdAndUserNameToInsightTelemetry() {
    val token = jwtAuthHelper.createJwtAccessToken(username = "bob", clientId = "hmpps-offender-events-client")
    req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")

    tracer.spanBuilder("span").startSpan().run {
      makeCurrent().use { clientTrackingInterceptor.preHandle(req, res, "null") }
      end()
    }
    otelTesting.assertTraces().hasTracesSatisfyingExactly({ t ->
      t.hasSpansSatisfyingExactly({
        it.hasAttribute(AttributeKey.stringKey("username"), "bob")
        it.hasAttribute(AttributeKey.stringKey("clientId"), "hmpps-offender-events-client")
      })
    })
  }

  @Test
  fun shouldAddClientIdAndUserNameToInsightTelemetryEvenIfTokenExpired() {
    val token = jwtAuthHelper.createJwtAccessToken(username = "Fred", clientId = "hmpps-offender-events-client", expiryTime = Duration.ofHours(-1L))
    req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")

    tracer.spanBuilder("span").startSpan().run {
      makeCurrent().use { clientTrackingInterceptor.preHandle(req, res, "null") }
      end()
    }
    otelTesting.assertTraces().hasTracesSatisfyingExactly({ t ->
      t.hasSpansSatisfyingExactly({
        it.hasAttribute(AttributeKey.stringKey("username"), "Fred")
        it.hasAttribute(AttributeKey.stringKey("clientId"), "hmpps-offender-events-client")
      })
    })
  }

  @Test
  fun shouldAddClientIpToInsightTelemetry() {
    val someIpAddress = "12.13.14.15"
    req.remoteAddr = someIpAddress

    tracer.spanBuilder("span").startSpan().run {
      makeCurrent().use { clientTrackingInterceptor.preHandle(req, res, "null") }
      end()
    }
    otelTesting.assertTraces().hasTracesSatisfyingExactly({ t ->
      t.hasSpansSatisfyingExactly({
        it.hasAttribute(AttributeKey.stringKey("clientIpAddress"), someIpAddress)
      })
    })
  }

  @Test
  fun shouldAddClientIpToInsightTelemetryWithoutPortNumber() {
    val someIpAddress = "12.13.14.15"
    req.remoteAddr = "$someIpAddress:6789"

    tracer.spanBuilder("span").startSpan().run {
      makeCurrent().use { clientTrackingInterceptor.preHandle(req, res, "null") }
      end()
    }
    otelTesting.assertTraces().hasTracesSatisfyingExactly({ t ->
      t.hasSpansSatisfyingExactly({
        it.hasAttribute(AttributeKey.stringKey("clientIpAddress"), someIpAddress)
      })
    })
  }

  private companion object {
    @JvmStatic
    @RegisterExtension
    private val otelTesting: OpenTelemetryExtension = OpenTelemetryExtension.create()
  }
}
