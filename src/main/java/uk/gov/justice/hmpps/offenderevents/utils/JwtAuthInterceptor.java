package uk.gov.justice.hmpps.offenderevents.utils;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class JwtAuthInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            final HttpRequest request, final byte[] body, final ClientHttpRequestExecution execution)
            throws IOException {

        final var headers = request.getHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, UserContext.getAuthToken());
        return execution.execute(request, body);
    }
}
