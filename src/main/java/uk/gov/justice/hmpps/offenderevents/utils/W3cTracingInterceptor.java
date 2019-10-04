package uk.gov.justice.hmpps.offenderevents.utils;

import com.microsoft.applicationinsights.web.internal.correlation.TraceContextCorrelation;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;

import java.io.IOException;

/**
 * Temporary hack to pass w3c tracing headers.  Once https://github.com/microsoft/ApplicationInsights-Java/issues/674 has
 * been fixed can be removed and switch to apache httpclient instead.
 */
public class W3cTracingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    @NonNull
    public ClientHttpResponse intercept(
            final HttpRequest request, @NonNull final byte[] body, @NonNull final ClientHttpRequestExecution execution)
            throws IOException {

        final var headers = request.getHeaders();
        headers.add("traceparent", TraceContextCorrelation.generateChildDependencyTraceparent());
        final var tracestate = TraceContextCorrelation.retriveTracestate();
        if (tracestate != null) {
            headers.add("tracestate", tracestate);
        }

        return execution.execute(request, body);
    }
}
