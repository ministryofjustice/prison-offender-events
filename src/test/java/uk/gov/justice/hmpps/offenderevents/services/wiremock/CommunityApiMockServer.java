package uk.gov.justice.hmpps.offenderevents.services.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;

public class CommunityApiMockServer extends WireMockServer {
    CommunityApiMockServer() {
        super(8087);
    }
}
