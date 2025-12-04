package com.hsbc.fraud.detection.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Interceptor for logging HTTP request/response details with timing information.
 * Works in conjunction with CorrelationIdFilter to provide comprehensive request tracking.
 */
@Component
public class LoggingInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingInterceptor.class);
    private static final String START_TIME_ATTRIBUTE = "startTime";
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Record start time for performance tracking
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        
        // Log request details
        LoggingContext.put("method", request.getMethod());
        LoggingContext.put("uri", request.getRequestURI());
        LoggingContext.put("remoteAddr", request.getRemoteAddr());
        
        logger.debug("Request started: {} {}", request.getMethod(), request.getRequestURI());
        
        return true;
    }
    
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, 
                          Object handler, ModelAndView modelAndView) {
        // Additional processing after handler execution but before view rendering
        logger.debug("Handler execution completed");
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                               Object handler, Exception ex) {
        // Calculate request duration
        Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            
            LoggingContext.put("durationMs", String.valueOf(duration));
            LoggingContext.put("status", String.valueOf(response.getStatus()));
            
            // Log with appropriate level based on status code and duration
            if (response.getStatus() >= 500) {
                logger.error("Request completed with server error: {} {} - status={}, duration={}ms",
                        request.getMethod(), request.getRequestURI(), 
                        response.getStatus(), duration);
            } else if (response.getStatus() >= 400) {
                logger.warn("Request completed with client error: {} {} - status={}, duration={}ms",
                        request.getMethod(), request.getRequestURI(), 
                        response.getStatus(), duration);
            } else if (duration > 1000) {
                logger.warn("Slow request detected: {} {} - duration={}ms",
                        request.getMethod(), request.getRequestURI(), duration);
            } else {
                logger.debug("Request completed: {} {} - status={}, duration={}ms",
                        request.getMethod(), request.getRequestURI(), 
                        response.getStatus(), duration);
            }
        }
        
        // Log exception if present
        if (ex != null) {
            logger.error("Request failed with exception: {} {}", 
                    request.getMethod(), request.getRequestURI(), ex);
        }
    }
}

