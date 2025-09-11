package com.example.imageservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LambdaCallbackRequest {
    
    @NotNull(message = "Job ID cannot be null")
    private String jobId;
    
    private String itemId;
    
    @NotNull(message = "Status cannot be null")
    private String status;
    
    private String cdnUrl;
    private String s3Key;
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    
    // Metadata mới
    private Integer dpiX;
    private Integer dpiY;
    private String format;
    private String colorSpace;
    private Integer channels;
    private Boolean hasAlpha;
    private Boolean isOpaque;
    private Integer orientation;
    private String profileName;
    private String compression;
    
    private String errorMessage;
    private String resultUrl;
    private Instant processedAt;
}