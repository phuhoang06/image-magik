package com.example.imageservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for Design Extraction from URL
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesignExtractionUrlRequest {
    
    @NotBlank(message = "Mockup URL is required")
    private String mockupUrl;
    
    @Builder.Default
    private String quality = "high"; // high, medium, low
    
    @Builder.Default
    private boolean backgroundRemoval = true;
    
    @Builder.Default
    private boolean trimEdges = true;
    
    @Builder.Default
    private boolean addPadding = true;
    
    private String jobId;
    
    private String callbackUrl;
    
    private String workerSecret;
}
