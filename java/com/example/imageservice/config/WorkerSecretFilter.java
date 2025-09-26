package com.example.imageservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1) // Chạy trước các filter khác
@Slf4j
public class WorkerSecretFilter extends OncePerRequestFilter {
    
    @Value("${lambda.callback.secret}")
    private String expectedWorkerSecret;
    
    @Value("${app.worker.enabled:false}")
    private boolean workerSecretEnabled;
    
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, 
                                  @NonNull HttpServletResponse response, 
                                  @NonNull FilterChain filterChain) throws ServletException, IOException {
        

        
        // Chỉ áp dụng cho lambda callback endpoint
        if (request.getRequestURI().equals("/api/v1/lambda/callback")) {
            

            
            if (!workerSecretEnabled) {
                log.warn("Worker secret validation is disabled");
                filterChain.doFilter(request, response);
                return;
            }
            
            if (expectedWorkerSecret == null || expectedWorkerSecret.trim().isEmpty()) {
                log.error("Worker secret is not configured");
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\":\"Worker secret not configured\"}");
                return;
            }
            
            String providedSecret = request.getHeader("X-Worker-Secret");
            
            if (providedSecret == null || providedSecret.trim().isEmpty()) {
                log.warn("Missing X-Worker-Secret header from: {}", request.getRemoteAddr());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Missing worker secret\"}");
                return;
            }
            
            if (!expectedWorkerSecret.equals(providedSecret)) {
                log.warn("Invalid worker secret from: {} (provided: {}, expected: {})", 
                        request.getRemoteAddr(), 
                        providedSecret.substring(0, Math.min(8, providedSecret.length())) + "...",
                        expectedWorkerSecret.substring(0, Math.min(8, expectedWorkerSecret.length())) + "...");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid worker secret\"}");
                return;
            }
            
            log.debug("Worker secret validation passed for: {}", request.getRemoteAddr());
        }
        
        filterChain.doFilter(request, response);
    }
    
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Chỉ filter cho lambda callback endpoint
        return !request.getRequestURI().equals("/api/v1/lambda/callback");
    }
}
