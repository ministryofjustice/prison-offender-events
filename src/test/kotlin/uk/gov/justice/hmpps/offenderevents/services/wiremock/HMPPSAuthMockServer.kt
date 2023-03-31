package uk.gov.justice.hmpps.offenderevents.services.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock

class HMPPSAuthMockServer internal constructor() : WireMockServer(8090) {
  fun stubGrantToken() {
    stubFor(
      WireMock.post(WireMock.urlEqualTo("/auth/oauth/token"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
                                      {
                                      "token_type": "bearer",
                                      "access_token": "ABCDE"
                                  }
                                
                                """
                .trimIndent(),
            ),
        ),
    )
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      WireMock.get("/auth/health/ping").willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }
}
