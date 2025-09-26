package com.example.imageservice.controller;

import com.example.imageservice.service.DesignDetectionService;
import com.example.imageservice.service.DesignExtractionService;
import com.example.imageservice.service.SimilaritySearchService;
import com.example.imageservice.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Controller for Design Detection API endpoints
 * Handles design detection requests using AI models
 */
@RestController
@RequestMapping("/api/v1/designs")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*") // Configure properly for production
public class DesignDetectionController {

    private final DesignDetectionService designDetectionService;
    private final DesignExtractionService designExtractionService;
    private final SimilaritySearchService similaritySearchService;
    private final RateLimitService rateLimitService;

    /**
     * Detect designs in uploaded mockup image
     * POST /api/v1/designs/detect
     */
    @PostMapping("/detect")
    public ResponseEntity<?> detectDesigns(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("image") MultipartFile imageFile) {

        try {
            // Extract user from JWT
            String userId = getUserId(jwt);
            log.info("Design detection request from user {} for image: {}", userId, imageFile.getOriginalFilename());

            // Check rate limit
            rateLimitService.checkUploadRateLimit(userId);

            // Validate image file
            if (imageFile.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "INVALID_REQUEST", "message", "Image file is required"));
            }

            // Check file size (50MB limit)
            if (imageFile.getSize() > 50 * 1024 * 1024) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "INVALID_REQUEST", "message", "Image file too large (max 50MB)"));
            }

            // Check if model is ready
            if (!designDetectionService.isModelReady()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "SERVICE_UNAVAILABLE", "message", "AI model is not ready"));
            }

            // Run design detection
            List<DesignDetectionService.DesignRegion> designRegions = designDetectionService.detectDesigns(imageFile);

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("userId", userId);
            response.put("imageName", imageFile.getOriginalFilename());
            response.put("imageSize", imageFile.getSize());
            response.put("detectedRegions", designRegions.size());
            response.put("designRegions", designRegions);
            response.put("modelInfo", designDetectionService.getModelInfo());

            log.info("Design detection completed for user {}. Found {} regions", userId, designRegions.size());
            return ResponseEntity.ok(response);

        } catch (RateLimitService.RateLimitExceededException e) {
            log.warn("Rate limit exceeded for design detection: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "RATE_LIMIT_EXCEEDED", "message", e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for design detection: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "INVALID_REQUEST", "message", e.getMessage()));

        } catch (Exception e) {
            log.error("Design detection failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "INTERNAL_ERROR", "message", "Design detection failed"));
        }
    }

    /**
     * Detect designs from image URL
     * POST /api/v1/designs/detect-url
     */
    @PostMapping("/detect-url")
    public ResponseEntity<?> detectDesignsFromUrl(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> request) {

        try {
            String userId = getUserId(jwt);
            String imageUrl = request.get("imageUrl");

            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "INVALID_REQUEST", "message", "imageUrl is required"));
            }

            log.info("Design detection request from user {} for URL: {}", userId, imageUrl);

            // Check rate limit
            rateLimitService.checkUploadRateLimit(userId);

            // Check if model is ready
            if (!designDetectionService.isModelReady()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "SERVICE_UNAVAILABLE", "message", "AI model is not ready"));
            }

            // Run design detection
            List<DesignDetectionService.DesignRegion> designRegions = designDetectionService.detectDesignsFromUrl(imageUrl);

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("userId", userId);
            response.put("imageUrl", imageUrl);
            response.put("detectedRegions", designRegions.size());
            response.put("designRegions", designRegions);
            response.put("modelInfo", designDetectionService.getModelInfo());

            log.info("URL-based design detection completed for user {}. Found {} regions", userId, designRegions.size());
            return ResponseEntity.ok(response);

        } catch (RateLimitService.RateLimitExceededException e) {
            log.warn("Rate limit exceeded for URL-based design detection: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "RATE_LIMIT_EXCEEDED", "message", e.getMessage()));

        } catch (Exception e) {
            log.error("URL-based design detection failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "INTERNAL_ERROR", "message", "URL-based design detection failed"));
        }
    }

    /**
     * Get AI model status and information
     * GET /api/v1/designs/model-info
     */
    @GetMapping("/model-info")
    public ResponseEntity<?> getModelInfo(@AuthenticationPrincipal Jwt jwt) {
        try {
            String userId = getUserId(jwt);
            log.debug("Model info request from user {}", userId);

            Map<String, Object> modelInfo = designDetectionService.getModelInfo();
            return ResponseEntity.ok(modelInfo);

        } catch (Exception e) {
            log.error("Failed to get model info: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "INTERNAL_ERROR", "message", "Failed to get model info"));
        }
    }

    /**
     * Health check for design detection service (no auth required)
     * GET /api/v1/designs/health
     */
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        try {
            boolean isReady = designDetectionService.isModelReady();
            Map<String, Object> health = Map.of(
                "service", "design-detection",
                "status", isReady ? "READY" : "NOT_READY",
                "modelReady", isReady,
                "timestamp", System.currentTimeMillis()
            );

            HttpStatus status = isReady ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            return ResponseEntity.status(status).body(health);

        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "HEALTH_CHECK_FAILED", "message", e.getMessage()));
        }
    }

    /**
     * Test endpoint for design detection service (no auth required)
     * GET /api/v1/designs/test
     */
    @GetMapping("/test")
    public ResponseEntity<?> testEndpoint() {
        try {
            Map<String, Object> response = Map.of(
                "message", "Design Detection API is working!",
                "service", "design-detection",
                "version", "1.0.0",
                "timestamp", System.currentTimeMillis(),
                "modelInfo", designDetectionService.getModelInfo()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Test endpoint failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "TEST_FAILED", "message", e.getMessage()));
        }
    }

    /**
     * Find similar designs using CLIP embeddings
     * POST /api/v1/designs/similar
     */
    @PostMapping("/similar")
    public ResponseEntity<?> findSimilarDesigns(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {
        try {
            String userId = getUserId(jwt);
            rateLimitService.checkUploadRateLimit(userId);
            
            log.info("Similar design search request from user {} with file: {}", userId, file.getOriginalFilename());
            
            // TODO: Implement similarity search with database
            // For now, return mock results
            Map<String, Object> response = Map.of(
                "status", "SUCCESS",
                "message", "Similar design search completed",
                "queryImage", file.getOriginalFilename(),
                "topK", topK,
                "similarDesigns", List.of(
                    Map.of("id", "design_001", "similarity", 0.95, "category", "logo"),
                    Map.of("id", "design_002", "similarity", 0.87, "category", "graphic design"),
                    Map.of("id", "design_003", "similarity", 0.82, "category", "illustration")
                ),
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error finding similar designs: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "SIMILARITY_SEARCH_FAILED", "message", e.getMessage()));
        }
    }

           /**
            * Extract design regions from detected regions
            * POST /api/v1/designs/extract
            */
           @PostMapping("/extract")
           public ResponseEntity<?> extractDesigns(
                   @AuthenticationPrincipal Jwt jwt,
                   @RequestParam("image") MultipartFile imageFile) {
               try {
                   String userId = getUserId(jwt);
                   log.info("Design extraction request from user {} for image: {}", userId, imageFile.getOriginalFilename());

                   // Check rate limit
                   rateLimitService.checkUploadRateLimit(userId);

                   // Validate file
                   if (imageFile.isEmpty()) {
                       return ResponseEntity.badRequest()
                           .body(Map.of("error", "FILE_EMPTY", "message", "No image file uploaded"));
                   }
                   
                   // Check file type
                   String contentType = imageFile.getContentType();
                   if (contentType == null || !contentType.startsWith("image/")) {
                       return ResponseEntity.badRequest()
                           .body(Map.of("error", "INVALID_FILE_TYPE", "message", "File must be an image"));
                   }

                   // First detect designs
                   List<DesignDetectionService.DesignRegion> detections = designDetectionService.detectDesigns(imageFile);
                   
                   // Then extract design images
                   List<DesignExtractionService.ExtractedDesign> extractedDesigns = 
                       designExtractionService.extractDesigns(imageFile, detections);

                   Map<String, Object> response = new HashMap<>();
                   response.put("success", true);
                   response.put("userId", userId);
                   response.put("imageName", imageFile.getOriginalFilename());
                   response.put("imageSize", imageFile.getSize());
                   response.put("detectedRegions", detections.size());
                   response.put("extractedDesigns", extractedDesigns.size());
                   response.put("designs", extractedDesigns);
                   response.put("timestamp", System.currentTimeMillis());

                   return ResponseEntity.ok(response);

               } catch (Exception e) {
                   log.error("Error extracting designs: {}", e.getMessage(), e);
                   return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
               }
           }

           /**
            * Test design detection with sample image (no auth required)
            * POST /api/v1/designs/test-detect
            */
           @PostMapping("/test-detect")
           public ResponseEntity<?> testDesignDetection(@RequestParam("file") MultipartFile file) {
        try {
            log.info("Test design detection request with file: {}", file.getOriginalFilename());
            
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "FILE_EMPTY", "message", "No file uploaded"));
            }
            
            // Check file type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "INVALID_FILE_TYPE", "message", "File must be an image"));
            }
            
            // Run detection
            List<DesignDetectionService.DesignRegion> detections = designDetectionService.detectDesigns(file);
            
            Map<String, Object> response = Map.of(
                "status", "SUCCESS",
                "message", "Design detection completed",
                "filename", file.getOriginalFilename(),
                "fileSize", file.getSize(),
                "contentType", contentType,
                "detectionCount", detections.size(),
                "detections", detections,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Test design detection failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "DETECTION_FAILED", "message", e.getMessage()));
        }
    }

    /**
     * Store design embeddings in Pinecone database
     * POST /api/v1/designs/store
     */
    @PostMapping("/store")
    public ResponseEntity<?> storeDesignEmbeddings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("image") MultipartFile imageFile) {
        try {
            String userId = getUserId(jwt);
            log.info("Store design embeddings request from user {} for image: {}", userId, imageFile.getOriginalFilename());

            // Check rate limit
            rateLimitService.checkUploadRateLimit(userId);

            // Validate file
            if (imageFile.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "FILE_EMPTY", "message", "No image file uploaded"));
            }
            
            // Check file type
            String contentType = imageFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "INVALID_FILE_TYPE", "message", "File must be an image"));
            }

            // Detect designs first
            List<DesignDetectionService.DesignRegion> detections = designDetectionService.detectDesigns(imageFile);
            
            // Generate embeddings and store in Pinecone
            List<SimilaritySearchService.DesignEmbedding> embeddings = new ArrayList<>();
            for (int i = 0; i < detections.size(); i++) {
                DesignDetectionService.DesignRegion region = detections.get(i);
                String designId = "design_" + userId + "_" + System.currentTimeMillis() + "_" + i;
                
                // Generate mock embedding (in real implementation, use CLIP)
                float[] embedding = generateMockEmbedding();
                
                embeddings.add(new SimilaritySearchService.DesignEmbedding(
                    designId,
                    region.getClassName(),
                    region.getConfidence(),
                    embedding
                ));
            }
            
            // Store embeddings in Pinecone
            similaritySearchService.storeDesignEmbeddingsBatch(embeddings);

            Map<String, Object> response = Map.of(
                "success", true,
                "userId", userId,
                "imageName", imageFile.getOriginalFilename(),
                "detectedRegions", detections.size(),
                "storedEmbeddings", embeddings.size(),
                "message", "Design embeddings stored successfully in Pinecone",
                "timestamp", System.currentTimeMillis()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error storing design embeddings: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "STORE_FAILED", "message", e.getMessage()));
        }
    }

    /**
     * Generate mock embedding for testing
     */
    private float[] generateMockEmbedding() {
        float[] embedding = new float[512]; // CLIP embedding size
        Random rand = new Random();
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = rand.nextFloat() * 2 - 1; // Range [-1, 1]
        }
        return embedding;
    }

    /**
     * Extract user ID from JWT token
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
}
