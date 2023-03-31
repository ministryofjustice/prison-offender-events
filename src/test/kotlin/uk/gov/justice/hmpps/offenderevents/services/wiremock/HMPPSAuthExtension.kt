package uk.gov.justice.hmpps.offenderevents.services.wiremock

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class HMPPSAuthExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  override fun afterAll(context: ExtensionContext): Unit = server.stop()

  override fun beforeAll(context: ExtensionContext) {
    server.stubGrantToken()
    server.start()
  }

  override fun beforeEach(context: ExtensionContext): Unit = server.resetRequests()

  companion object {
    val server = HMPPSAuthMockServer()
  }
}
