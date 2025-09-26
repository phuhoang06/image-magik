package com.example.imageservice.controller;

import com.example.imageservice.service.JavaImageProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for testing Java Image Processing
 */
@RestController
@RequestMapping("/api/v1/image-processing")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ImageProcessingController {

    private final JavaImageProcessingService javaImageProcessingService;

    /**
     * Test design extraction with Java image processing
     * POST /api/v1/image-processing/extract-design
     */
    @PostMapping("/extract-design")
    public ResponseEntity<?> extractDesign(@RequestBody Map<String, Object> request) {
        try {
            String mockupUrl = (String) request.get("mockupUrl");
            String jobId = (String) request.get("jobId");
            
            if (mockupUrl == null || jobId == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "mockupUrl and jobId are required"
                ));
            }
            
            // Create options
            Map<String, Object> options = new HashMap<>();
            options.put("backgroundRemoval", request.getOrDefault("backgroundRemoval", true));
            options.put("trimEdges", request.getOrDefault("trimEdges", true));
            options.put("addPadding", request.getOrDefault("addPadding", true));
            options.put("quality", request.getOrDefault("quality", "high"));
            
            log.info("Testing design extraction for jobId: {}", jobId);
            
            // Process design extraction
            String designUrl = javaImageProcessingService.extractDesignFromMockup(mockupUrl, jobId, options);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "jobId", jobId,
                "designUrl", designUrl,
                "message", "Design extraction completed successfully"
            ));
            
        } catch (Exception e) {
            log.error("Error in design extraction test", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Health check for image processing service
     * GET /api/v1/image-processing/health
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "Java Image Processing Service",
            "timestamp", System.currentTimeMillis()
        ));
    }
}
