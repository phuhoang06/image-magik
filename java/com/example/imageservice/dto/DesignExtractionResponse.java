package com.example.imageservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response DTO for Design Extraction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesignExtractionResponse {
    
    private boolean success;
    
    private String error;
    
    private String jobId;
    
    private String designUrl;
    
    private String designId;
    
    private Map<String, Object> validation;
    
    private Map<String, Object> metadata;
    
    private LocalDateTime processedAt;
    
    private String status; // PROCESSING, COMPLETED, FAILED
    
    private Double confidence;
    
    private Double extractionTime; // Time taken for extraction in seconds
    
    private Map<String, Object> extractionOptions;
    
    // Helper methods
    public boolean isSuccess() {
        return success;
    }
    
    public boolean isProcessing() {
        return "PROCESSING".equals(status);
    }
    
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }
    
    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}

