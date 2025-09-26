package com.example.imageservice.controller;

import com.example.imageservice.dto.UploadRequest;
import com.example.imageservice.dto.UploadResponse;
import com.example.imageservice.dto.JobStatusResponse;

import com.example.imageservice.entity.UploadJob;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.beans.factory.annotation.Value;
import com.example.imageservice.service.UploadGatewayService;
import com.example.imageservice.service.RateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*") // Configure properly for production
public class UploadController {

    private final UploadGatewayService uploadGatewayService;
    private final RateLimitService rateLimitService;

    @Value("${lambda.callback.secret}")
    private String lambdaCallbackSecretProperty;

    /**
     * Create new upload job
     * POST /api/v1/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<?> createUpload(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UploadRequest request) {

        try {
            // Extract user from Jwt provided by Resource Server
            String userId = getUserId(jwt);

            log.info("Upload request from user {} with {} URLs", userId, request.getUrls().size());

            // Check rate limit for upload endpoint (1 request/second)
            rateLimitService.checkUploadRateLimit(userId);

            // Create upload job
            UploadJob job = uploadGatewayService.createUploadJob(userId, request.getUrls());

            // Return response
            UploadResponse response = UploadResponse.builder()
                    .jobId(job.getJobId())
                    .status(job.getStatus().name())
                    .total(job.getTotal())
                    .completed(job.getCompleted())
                    .failed(job.getFailed())
                    .createdAt(job.getCreatedAt())
                    .message("Upload job created successfully")
                    .build();

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (RateLimitService.RateLimitExceededException e) {
            log.warn("Rate limit exceeded: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "RATE_LIMIT_EXCEEDED", "message", e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "INVALID_REQUEST", "message", e.getMessage()));

        } catch (Exception e) {
            log.error("Upload creation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "Upload creation failed"));
        }
    }

    /**
     * Get job status
     * GET /api/v1/jobs/{jobId}
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> getJobStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String jobId) {

        try {
            String userId = getUserId(jwt);

            log.debug("Job status request for job {} from user {}", jobId, userId);

            // Get job
            UploadJob job = uploadGatewayService.getJobByIdAndUserId(jobId, userId);
            if (job == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "JOB_NOT_FOUND", "message", "Job not found"));
            }

            // Get CDN URLs if job is completed
            List<String> cdnUrls = uploadGatewayService.getCdnUrls(job);

            JobStatusResponse response = JobStatusResponse.builder()
                    .jobId(job.getJobId())
                    .status(job.getStatus().name())
                    .total(job.getTotal())
                    .completed(job.getCompleted())
                    .failed(job.getFailed())
                    .createdAt(job.getCreatedAt())
                    .finishedAt(job.getFinishedAt())
                    .processingTimeSeconds(job.getProcessingTimeSeconds())
                    .cdnUrls(cdnUrls)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Get job status failed for job {}: {}", jobId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "Failed to get job status"));
        }
    }

    /**
     * Get job details with items
     * GET /api/v1/jobs/{jobId}/results
     */
    @GetMapping("/jobs/{jobId}/results")
    public ResponseEntity<?> getJobResults(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String jobId) {

        try {
            String userId = getUserId(jwt);

            log.debug("Job results request for job {} from user {}", jobId, userId);

            // Get job with items
            UploadJob job = uploadGatewayService.getJobWithItems(jobId, userId);
            if (job == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "JOB_NOT_FOUND", "message", "Job not found"));
            }

            return ResponseEntity.ok(job);

        } catch (Exception e) {
            log.error("Get job results failed for job {}: {}", jobId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "Failed to get job results"));
        }
    }



    /**
     * Get user's recent jobs
     * GET /api/v1/jobs
     */
    @GetMapping("/jobs")
    public ResponseEntity<?> getUserJobs(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "10") int limit) {

        try {
            String userId = getUserId(jwt);

            // Limit the limit :)
            limit = Math.min(limit, 50);

            List<UploadJob> jobs = uploadGatewayService.getUserRecentJobs(userId, limit);

            return ResponseEntity.ok(Map.of(
                    "jobs", jobs,
                    "total", jobs.size()
            ));

        } catch (Exception e) {
            log.error("Get user jobs failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "Failed to get user jobs"));
        }
    }

    /**
     * Get user's rate limit status
     * GET /api/v1/rate-limit
     */
    @GetMapping("/rate-limit")
    public ResponseEntity<?> getRateLimitStatus(@AuthenticationPrincipal Jwt jwt) {

        try {
            String userId = getUserId(jwt);

            RateLimitService.RateLimitStatus status = rateLimitService.getRateLimitStatus(userId);

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Get rate limit status failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "Failed to get rate limit status"));
        }
    }

    /**
     * Test rate limit endpoint (for debugging)
     * POST /api/v1/test-rate-limit
     */
    @PostMapping("/test-rate-limit")
    public ResponseEntity<?> testRateLimit(@AuthenticationPrincipal Jwt jwt) {

        try {
            String userId = getUserId(jwt);

            log.info("Test rate limit request from user {}", userId);

            // Check rate limit
            rateLimitService.checkUploadRateLimit(userId);

            return ResponseEntity.ok(Map.of(
                    "message", "Rate limit test passed",
                    "userId", userId,
                    "timestamp", java.time.LocalDateTime.now()
            ));

        } catch (RateLimitService.RateLimitExceededException e) {
            log.warn("Rate limit test failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "RATE_LIMIT_EXCEEDED", "message", e.getMessage()));

        } catch (Exception e) {
            log.error("Rate limit test failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "Rate limit test failed"));
        }
    }

    /**
     * Debug Redis keys endpoint (for debugging)
     * GET /api/v1/debug/redis-keys
     */
    @GetMapping("/debug/redis-keys")
    public ResponseEntity<?> debugRedisKeys(@AuthenticationPrincipal Jwt jwt) {

        try {
            String userId = getUserId(jwt);
            
            // Get Redis key for upload endpoint
            String uploadKey = "rate_limit:" + userId + ":upload";
            Object uploadValue = rateLimitService.getRedisValue(uploadKey);
            
            // Get rate limit status
            RateLimitService.RateLimitStatus status = rateLimitService.getRateLimitStatus(userId);
            
            return ResponseEntity.ok(Map.of(
                    "userId", userId,
                    "currentTime", java.time.LocalDateTime.now(),
                    "uploadKey", uploadKey,
                    "uploadValue", uploadValue,
                    "rateLimitStatus", status
            ));

        } catch (Exception e) {
            log.error("Debug Redis keys failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "Debug failed"));
        }
    }


    /**
     * Extract JWT token from Authorization header
     */
    private String getUserId(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("Missing authentication");
        }
        String sub = jwt.getSubject();
        if (sub != null && !sub.isBlank()) {
            return sub;
        }
        String email = jwt.getClaim("email");
        if (email != null && !email.isBlank()) {
            return email;
        }
        String username = jwt.getClaim("username");
        if (username != null && !username.isBlank()) {
            return username;
        }
        return "anonymous";
    }


    /**
     * Exception handler for validation errors
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationErrors(org.springframework.web.bind.MethodArgumentNotValidException ex) {

        String errorMessage = ex.getBindingResult().getAllErrors().stream()
                .map(org.springframework.validation.ObjectError::getDefaultMessage)
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "VALIDATION_ERROR", "message", errorMessage));
    }

    /**
     * Exception handler for JWT errors
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleJwtErrors(RuntimeException ex) {
        throw ex;
    }
}