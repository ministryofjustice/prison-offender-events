package uk.gov.justice.hmpps.offenderevents.services.wiremock;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class HMPPSAuthExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
    public static final HMPPSAuthMockServer server = new HMPPSAuthMockServer();

    @Override
    public void afterAll(ExtensionContext context) {
        server.stop();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        server.stubGrantToken();
        server.start();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        server.resetRequests();
    }
}

