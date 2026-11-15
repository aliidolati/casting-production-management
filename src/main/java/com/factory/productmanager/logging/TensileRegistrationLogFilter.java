package com.factory.productmanager.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.factory.productmanager.logging.TensileRegistrationLog.ACTOR_ID;
import static com.factory.productmanager.logging.TensileRegistrationLog.LOG;
import static com.factory.productmanager.logging.TensileRegistrationLog.REQUEST_ID;
import static com.factory.productmanager.logging.TensileRegistrationLog.elapsedMs;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TensileRegistrationLogFilter extends OncePerRequestFilter {
    private static final Pattern ROUTE = Pattern.compile(
            "/api/pieces/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/tensile-tests");
    private static final String REQUEST_ID_HEADER = "X-Tensile-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var route = ROUTE.matcher(request.getRequestURI().substring(request.getContextPath().length()));
        if (!"POST".equals(request.getMethod()) || !route.matches()) {
            chain.doFilter(request, response);
            return;
        }

        String pieceId = route.group(1);
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        try (var ignored = MDC.putCloseable(REQUEST_ID, requestId)) {
            response.setHeader(REQUEST_ID_HEADER, requestId);
            LOG.info("event=request_received pieceId={}", pieceId);
            try {
                chain.doFilter(request, response);
                LOG.info("event=request_finished pieceId={} httpStatus={} elapsedMs={}",
                        pieceId, response.getStatus(), elapsedMs(startedAt));
            } catch (IOException | ServletException | RuntimeException failure) {
                LOG.warn("event=request_failed pieceId={} errorType={} elapsedMs={}",
                        pieceId, failure.getClass().getSimpleName(), elapsedMs(startedAt));
                throw failure;
            } finally {
                MDC.remove(ACTOR_ID);
            }
        }
    }
}
