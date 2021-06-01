package uk.gov.justice.hmpps.offenderevents.services.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

public class HMPPSAuthMockServer extends WireMockServer {
    HMPPSAuthMockServer() {
        super(8090);
    }

    public void stubGrantToken() {
        stubFor(
            WireMock.post(WireMock.urlEqualTo("/auth/oauth/token"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                                      {
                                      "token_type": "bearer",
                                      "access_token": "ABCDE"
                                  }
                                """
                        )
                )
        )
        ;
    }

}
