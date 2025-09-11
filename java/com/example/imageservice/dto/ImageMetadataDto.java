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
public class ImageMetadataDto {
    
    private String itemId;
    private String jobId;
    private String originalUrl;
    private String cdnUrl;
    private String s3Key;
    
    // Basic info
    private Long sizeBytes;
    private String sizeFormatted;
    private Integer width;
    private Integer height;
    private String dimensions;
    
    // Technical metadata
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
    
    // Status
    private String status;
    private String errorMessage;
    
    // Timestamps
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime processedAt;
    
    /**
     * Format file size to human readable format
     */
    public String getSizeFormatted() {
        if (sizeBytes == null) return "Unknown";
        
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        } else if (sizeBytes < 1024 * 1024) {
            return String.format("%.1f KB", sizeBytes / 1024.0);
        } else if (sizeBytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * Get dimensions as string
     */
    public String getDimensions() {
        if (width == null || height == null) return "Unknown";
        return width + " x " + height;
    }
    
    /**
     * Get DPI as string
     */
    public String getDpiFormatted() {
        if (dpiX == null || dpiY == null) return "Unknown";
        if (dpiX.equals(dpiY)) {
            return dpiX + " DPI";
        } else {
            return dpiX + " x " + dpiY + " DPI";
        }
    }
}
