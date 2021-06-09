package uk.gov.justice.hmpps.offenderevents.services.wiremock;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class CommunityApiExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
    public static final CommunityApiMockServer server = new CommunityApiMockServer();

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

