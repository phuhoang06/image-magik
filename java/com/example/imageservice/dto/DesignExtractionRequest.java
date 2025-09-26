package com.example.imageservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for Design Extraction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesignExtractionRequest {
    
    @NotNull(message = "Mockup file is required")
    private MultipartFile mockupFile;
    
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
