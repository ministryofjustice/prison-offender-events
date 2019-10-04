package uk.gov.justice.hmpps.offenderevents.utils;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import uk.gov.justice.hmpps.offenderevents.config.SecurityUserContext;

import javax.servlet.*;
import java.io.IOException;

@Slf4j
@Component
@Order(1)
public class UserMdcFilter implements Filter {
    private static final String USER_ID_HEADER = "userId";

    private final SecurityUserContext securityUserContext;

    @Autowired
    public UserMdcFilter(final SecurityUserContext securityUserContext) {
        this.securityUserContext = securityUserContext;
    }

    @Override
    public void init(final FilterConfig filterConfig) {
        // Initialise - no functionality
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {

        final var currentUsername = securityUserContext.getCurrentUsername();

        try {
            if (currentUsername != null) {
                MDC.put(USER_ID_HEADER, currentUsername);
            }
            chain.doFilter(request, response);
        } finally {
            if (currentUsername != null) {
                MDC.remove(USER_ID_HEADER);
            }
        }
    }

    @Override
    public void destroy() {
        // Destroy - no functionality
    }
}
