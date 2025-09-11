package com.example.imageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${rate-limit.enabled:true}")
    private boolean rateLimitEnabled;
    
    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";
    
    /**
     * Check rate limit for upload endpoint (1 request/second per user)
     */
    public void checkUploadRateLimit(String userId) {
        if (!rateLimitEnabled) {
            return;
        }
        
        String key = RATE_LIMIT_KEY_PREFIX + userId + ":upload";
        
        try {
            // Atomic SET NX EX operation
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "1", 1, TimeUnit.SECONDS);
            
            if (success == null || !success) {
                log.warn("Rate limit exceeded for user {} - upload endpoint", userId);
                throw new RateLimitExceededException(
                    "You can only call /upload once per second."
                );
            }
            
            log.info("Rate limit check passed for user {} - upload endpoint", userId);
            
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error checking rate limit for user {}: {}", userId, e.getMessage());
            // Don't block request if Redis is down
        }
    }
    
    /**
     * Get Redis value for debugging
     */
    public Object getRedisValue(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Error getting Redis value for key {}: {}", key, e.getMessage());
            return null;
        }
    }
    
    /**
     * Reset rate limit for user (admin function)
     */
    public void resetUserRateLimit(String userId) {
        try {
            String key = RATE_LIMIT_KEY_PREFIX + userId + ":upload";
            redisTemplate.delete(key);
            log.info("Rate limit reset for user {}", userId);
            
        } catch (Exception e) {
            log.error("Error resetting rate limit for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to reset rate limit", e);
        }
    }
    
    /**
     * Get current rate limit status for user
     */
    public RateLimitStatus getRateLimitStatus(String userId) {
        String key = RATE_LIMIT_KEY_PREFIX + userId + ":upload";
        
        try {
            Object value = redisTemplate.opsForValue().get(key);
            Long ttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
            
            boolean isBlocked = value != null;
            
            return RateLimitStatus.builder()
                    .userId(userId)
                    .endpoint("upload")
                    .isBlocked(isBlocked)
                    .remainingTimeMs(ttl != null ? ttl : 0)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error getting rate limit status for user {}: {}", userId, e.getMessage());
            return RateLimitStatus.builder()
                    .userId(userId)
                    .endpoint("upload")
                    .error(e.getMessage())
                    .build();
        }
    }
    
    /**
     * Rate limit status data class
     */
    @lombok.Builder
    @lombok.Data
    public static class RateLimitStatus {
        private String userId;
        private String endpoint;
        private boolean isBlocked;
        private long remainingTimeMs;
        private String error;
        
        public boolean canMakeRequest() {
            return !isBlocked;
        }
        
        public double getRemainingSeconds() {
            return remainingTimeMs / 1000.0;
        }
    }
    
    /**
     * Rate limit exceeded exception
     */
    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}