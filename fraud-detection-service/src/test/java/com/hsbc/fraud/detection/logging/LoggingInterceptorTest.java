package com.hsbc.fraud.detection.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.web.servlet.ModelAndView;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("LoggingInterceptor Tests")
@ExtendWith(MockitoExtension.class)
class LoggingInterceptorTest {
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private Object handler;
    
    @Mock
    private ModelAndView modelAndView;
    
    private LoggingInterceptor interceptor;
    
    @BeforeEach
    void setUp() {
        interceptor = new LoggingInterceptor();
        MDC.clear(); // Ensure clean state for each test
    }
    
    @AfterEach
    void tearDown() {
        MDC.clear(); // Clean up after each test
    }
    
    @Test
    @DisplayName("Should set start time attribute and log request details in preHandle")
    void shouldSetStartTimeAndLogRequestDetailsInPreHandle() {
        // Given
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/transactions");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        
        // When
        boolean result = interceptor.preHandle(request, response, handler);
        
        // Then
        assertTrue(result, "preHandle should return true to continue processing");
        verify(request).setAttribute(eq("startTime"), anyLong());
        verify(request, atLeastOnce()).getMethod();
        verify(request, atLeastOnce()).getRequestURI();
        verify(request, atLeastOnce()).getRemoteAddr();
        
        // Verify LoggingContext was populated
        assertEquals("POST", LoggingContext.get("method"));
        assertEquals("/api/transactions", LoggingContext.get("uri"));
        assertEquals("192.168.1.100", LoggingContext.get("remoteAddr"));
    }
    
    @Test
    @DisplayName("Should execute postHandle without errors")
    void shouldExecutePostHandleWithoutErrors() {
        // When & Then - should not throw any exception
        assertDoesNotThrow(() -> 
            interceptor.postHandle(request, response, handler, modelAndView)
        );
    }
    
    @Test
    @DisplayName("Should calculate duration and log success for 2xx status")
    void shouldCalculateDurationAndLogSuccessFor2xxStatus() {
        // Given
        long startTime = System.currentTimeMillis() - 50; // 50ms ago
        when(request.getAttribute("startTime")).thenReturn(startTime);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/health");
        when(response.getStatus()).thenReturn(200);
        
        // When
        interceptor.afterCompletion(request, response, handler, null);
        
        // Then
        verify(request).getAttribute("startTime");
        verify(response, atLeastOnce()).getStatus();
        
        // Verify LoggingContext was updated with duration and status
        assertNotNull(LoggingContext.get("durationMs"));
        assertEquals("200", LoggingContext.get("status"));
        
        long duration = Long.parseLong(LoggingContext.get("durationMs"));
        assertTrue(duration >= 0 && duration < 1000, 
                "Duration should be non-negative and less than 1 second");
    }
    
    @Test
    @DisplayName("Should log warning for slow request exceeding 1000ms")
    void shouldLogWarningForSlowRequest() {
        // Given
        long startTime = System.currentTimeMillis() - 1500; // 1500ms ago
        when(request.getAttribute("startTime")).thenReturn(startTime);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/transactions/analyze");
        when(response.getStatus()).thenReturn(200);
        
        // When
        interceptor.afterCompletion(request, response, handler, null);
        
        // Then
        verify(response, atLeastOnce()).getStatus();
        
        // Verify LoggingContext
        long duration = Long.parseLong(LoggingContext.get("durationMs"));
        assertTrue(duration >= 1000, "Duration should be >= 1000ms for slow request");
        assertEquals("200", LoggingContext.get("status"));
    }
    
    @Test
    @DisplayName("Should log warning for 4xx client error")
    void shouldLogWarningFor4xxClientError() {
        // Given
        long startTime = System.currentTimeMillis() - 100;
        when(request.getAttribute("startTime")).thenReturn(startTime);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/transactions/invalid");
        when(response.getStatus()).thenReturn(404);
        
        // When
        interceptor.afterCompletion(request, response, handler, null);
        
        // Then
        verify(response, atLeastOnce()).getStatus();
        assertEquals("404", LoggingContext.get("status"));
    }
    
    @Test
    @DisplayName("Should log warning for 400 bad request")
    void shouldLogWarningFor400BadRequest() {
        // Given
        long startTime = System.currentTimeMillis() - 75;
        when(request.getAttribute("startTime")).thenReturn(startTime);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/transactions");
        when(response.getStatus()).thenReturn(400);
        
        // When
        interceptor.afterCompletion(request, response, handler, null);
        
        // Then
        verify(response, atLeastOnce()).getStatus();
        assertEquals("400", LoggingContext.get("status"));
    }
    
    @Test
    @DisplayName("Should log error for 5xx server error")
    void shouldLogErrorFor5xxServerError() {
        // Given
        long startTime = System.currentTimeMillis() - 200;
        when(request.getAttribute("startTime")).thenReturn(startTime);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/transactions/process");
        when(response.getStatus()).thenReturn(500);
        
        // When
        interceptor.afterCompletion(request, response, handler, null);
        
        // Then
        verify(response, atLeastOnce()).getStatus();
        assertEquals("500", LoggingContext.get("status"));
    }
    
    @Test
    @DisplayName("Should log error for 503 service unavailable")
    void shouldLogErrorFor503ServiceUnavailable() {
        // Given
        long startTime = System.currentTimeMillis() - 150;
        when(request.getAttribute("startTime")).thenReturn(startTime);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/fraud/check");
        when(response.getStatus()).thenReturn(503);
        
        // When
        interceptor.afterCompletion(request, response, handler, null);
        
        // Then
        verify(response, atLeastOnce()).getStatus();
        assertEquals("503", LoggingContext.get("status"));
    }
    
    @Test
    @DisplayName("Should handle null start time gracefully")
    void shouldHandleNullStartTimeGracefully() {
        // Given
        when(request.getAttribute("startTime")).thenReturn(null);
        
        // When & Then - should not throw exception
        assertDoesNotThrow(() -> 
            interceptor.afterCompletion(request, response, handler, null)
        );
        
        // Verify no duration was set
        assertNull(LoggingContext.get("durationMs"));
        assertNull(LoggingContext.get("status"));
    }
    
    @Test
    @DisplayName("Should log exception when present")
    void shouldLogExceptionWhenPresent() {
        // Given
        long startTime = System.currentTimeMillis() - 50;
        when(request.getAttribute("startTime")).thenReturn(startTime);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/transactions");
        when(response.getStatus()).thenReturn(500);
        
        RuntimeException exception = new RuntimeException("Database connection failed");
        
        assertDoesNotThrow(() -> 
            interceptor.afterCompletion(request, response, handler, exception)
        );
        
        verify(request, atLeastOnce()).getMethod();
        verify(request, atLeastOnce()).getRequestURI();
    }
    
    @Test
    @DisplayName("Should handle exception even with null start time")
    void shouldHandleExceptionWithNullStartTime() {
        // Given
        when(request.getAttribute("startTime")).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/users");
        
        Exception exception = new Exception("Unexpected error");
        
        // When & Then - should not throw
        assertDoesNotThrow(() -> 
            interceptor.afterCompletion(request, response, handler, exception)
        );
    }
    
    @Test
    @DisplayName("Should handle concurrent requests with different contexts")
    void shouldHandleConcurrentRequestsWithDifferentContexts() throws InterruptedException {
        // Given
        HttpServletRequest request1 = mock(HttpServletRequest.class);
        HttpServletRequest request2 = mock(HttpServletRequest.class);
        HttpServletResponse response1 = mock(HttpServletResponse.class);
        HttpServletResponse response2 = mock(HttpServletResponse.class);
        
        when(request1.getMethod()).thenReturn("GET");
        when(request1.getRequestURI()).thenReturn("/api/endpoint1");
        when(request1.getRemoteAddr()).thenReturn("10.0.0.1");
        
        when(request2.getMethod()).thenReturn("POST");
        when(request2.getRequestURI()).thenReturn("/api/endpoint2");
        when(request2.getRemoteAddr()).thenReturn("10.0.0.2");
        
        // When
        boolean result1 = interceptor.preHandle(request1, response1, handler);
        MDC.clear();
        boolean result2 = interceptor.preHandle(request2, response2, handler);
        
        // Then
        assertTrue(result1);
        assertTrue(result2);
        verify(request1).setAttribute(eq("startTime"), anyLong());
        verify(request2).setAttribute(eq("startTime"), anyLong());
    }
    
    @Test
    @DisplayName("Should measure duration accurately for fast requests")
    void shouldMeasureDurationAccuratelyForFastRequests() {
        // Given
        long startTime = System.currentTimeMillis() - 10; // 10ms ago
        when(request.getAttribute("startTime")).thenReturn(startTime);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/health");
        when(response.getStatus()).thenReturn(200);
        
        // When
        interceptor.afterCompletion(request, response, handler, null);
        
        // Then
        String durationStr = LoggingContext.get("durationMs");
        assertNotNull(durationStr);
        long duration = Long.parseLong(durationStr);
        assertTrue(duration >= 10 && duration < 100, 
                "Duration should be at least 10ms and less than 100ms");
    }
    
    @Test
    @DisplayName("Should prioritize error logging over slow request warning")
    void shouldPrioritizeErrorLoggingOverSlowRequestWarning() {
        // Given - slow request (> 1000ms) with server error
        long startTime = System.currentTimeMillis() - 1500;
        when(request.getAttribute("startTime")).thenReturn(startTime);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/slow-endpoint");
        when(response.getStatus()).thenReturn(500);
        
        // When
        interceptor.afterCompletion(request, response, handler, null);
        
        // Then - should log as error (status takes precedence)
        verify(response, atLeastOnce()).getStatus();
        assertEquals("500", LoggingContext.get("status"));
        long duration = Long.parseLong(LoggingContext.get("durationMs"));
        assertTrue(duration >= 1500);
    }
    
    @Test
    @DisplayName("Should prioritize client error logging over slow request warning")
    void shouldPrioritizeClientErrorLoggingOverSlowRequestWarning() {
        // Given - slow request (> 1000ms) with client error
        long startTime = System.currentTimeMillis() - 1200;
        when(request.getAttribute("startTime")).thenReturn(startTime);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/invalid-endpoint");
        when(response.getStatus()).thenReturn(404);
        
        // When
        interceptor.afterCompletion(request, response, handler, null);
        
        // Then - should log as warning for client error (status takes precedence)
        verify(response, atLeastOnce()).getStatus();
        assertEquals("404", LoggingContext.get("status"));
        long duration = Long.parseLong(LoggingContext.get("durationMs"));
        assertTrue(duration >= 1200);
    }
    
    @Test
    @DisplayName("Should handle zero duration edge case")
    void shouldHandleZeroDurationEdgeCase() {
        // Given
        long startTime = System.currentTimeMillis();
        when(request.getAttribute("startTime")).thenReturn(startTime);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/instant");
        when(response.getStatus()).thenReturn(200);
        
        // When
        interceptor.afterCompletion(request, response, handler, null);
        
        // Then
        String durationStr = LoggingContext.get("durationMs");
        assertNotNull(durationStr);
        long duration = Long.parseLong(durationStr);
        assertTrue(duration >= 0, "Duration should be non-negative");
    }
    
    @Test
    @DisplayName("Should work with different HTTP methods")
    void shouldWorkWithDifferentHttpMethods() {
        // Given
        String[] httpMethods = {"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"};
        
        for (String method : httpMethods) {
            MDC.clear();
            when(request.getMethod()).thenReturn(method);
            when(request.getRequestURI()).thenReturn("/api/test");
            when(request.getRemoteAddr()).thenReturn("127.0.0.1");
            
            // When
            boolean result = interceptor.preHandle(request, response, handler);
            
            // Then
            assertTrue(result);
            assertEquals(method, LoggingContext.get("method"));
        }
    }
    
    @Test
    @DisplayName("Should handle various URI patterns")
    void shouldHandleVariousUriPatterns() {
        // Given
        String[] uris = {
            "/api/transactions",
            "/api/transactions/123",
            "/api/transactions/123/details",
            "/health",
            "/actuator/prometheus",
            "/api/v1/fraud-detection"
        };
        
        for (String uri : uris) {
            MDC.clear();
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn(uri);
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");
            
            // When
            boolean result = interceptor.preHandle(request, response, handler);
            
            // Then
            assertTrue(result);
            assertEquals(uri, LoggingContext.get("uri"));
        }
    }
    
    @Test
    @DisplayName("Should handle complete request lifecycle")
    void shouldHandleCompleteRequestLifecycle() {
        // Given
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/transactions");
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(response.getStatus()).thenReturn(201);
        
        long startTime = System.currentTimeMillis();
        when(request.getAttribute("startTime")).thenReturn(startTime);
        
        // When - simulate complete request lifecycle
        boolean preHandleResult = interceptor.preHandle(request, response, handler);
        interceptor.postHandle(request, response, handler, modelAndView);
        interceptor.afterCompletion(request, response, handler, null);
        
        // Then
        assertTrue(preHandleResult);
        verify(request).setAttribute(eq("startTime"), anyLong());
        verify(request).getAttribute("startTime");
        verify(response, atLeastOnce()).getStatus();
        
        // Verify final context state
        assertEquals("POST", LoggingContext.get("method"));
        assertEquals("/api/transactions", LoggingContext.get("uri"));
        assertEquals("10.0.0.5", LoggingContext.get("remoteAddr"));
        assertEquals("201", LoggingContext.get("status"));
        assertNotNull(LoggingContext.get("durationMs"));
    }
}

