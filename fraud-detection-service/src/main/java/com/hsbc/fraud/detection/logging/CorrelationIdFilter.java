package com.hsbc.fraud.detection.logging;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Servlet filter that ensures every HTTP request has a correlation ID for distributed tracing.
 * If the request already has a correlation ID in the header, it uses that; otherwise, it generates a new one.
 * The correlation ID is added to MDC and propagated in response headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(CorrelationIdFilter.class);
    
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String TRACE_ID_HEADER = "X-Trace-ID";
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        try {
            // Get or generate correlation ID
            String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isEmpty()) {
                correlationId = CorrelationIdGenerator.generate();
                logger.debug("Generated new correlation ID: {}", correlationId);
            } else {
                logger.debug("Using existing correlation ID: {}", correlationId);
            }
            
            // Get or generate trace ID
            String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isEmpty()) {
                traceId = CorrelationIdGenerator.generate();
            }
            
            // Set in MDC for all subsequent logs
            LoggingContext.setCorrelationId(correlationId);
            LoggingContext.setTraceId(traceId);
            
            // Add to response headers for client tracking
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
            httpResponse.setHeader(TRACE_ID_HEADER, traceId);
            
            // Log request details
            logger.info("Incoming request: method={}, uri={}, correlationId={}", 
                    httpRequest.getMethod(), 
                    httpRequest.getRequestURI(), 
                    correlationId);
            
            // Continue with the request
            chain.doFilter(request, response);
            
            // Log response
            logger.info("Completed request: status={}, correlationId={}", 
                    httpResponse.getStatus(), 
                    correlationId);
            
        } finally {
            // Clean up MDC after request processing
            LoggingContext.clear();
        }
    }
    
    @Override
    public void init(FilterConfig filterConfig) {
        logger.info("CorrelationIdFilter initialized");
    }
    
    @Override
    public void destroy() {
        logger.info("CorrelationIdFilter destroyed");
    }
}

