package com.example.imageservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemDetailDto {
    
    private String itemId;
    private String jobId;
    private String originalUrl;
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
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime processedAt;
}