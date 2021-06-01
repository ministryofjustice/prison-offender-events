package uk.gov.justice.hmpps.offenderevents.services.wiremock;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class PrisonApiExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
    public static final PrisonApiMockServer server = new PrisonApiMockServer();

    @Override
    public void afterAll(ExtensionContext context) {
        server.stop();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        server.start();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        server.resetRequests();
    }
}

