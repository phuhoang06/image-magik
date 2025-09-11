package com.example.imageservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrichedMetadataDto {
    
    // GPS coordinates
    private Double gpsLatitude;
    private Double gpsLongitude;
    
    // Camera information
    private String cameraMake;
    private String cameraModel;
    private String focalLength;
    
    // Date information
    private String dateTaken;
    
    // Technical details
    private Integer orientation;
    private String iccProfile;
    private String colorSpace;
    private String compression;
    private Integer quality;
    private String bitDepth;
    private String format;
    
    /**
     * Get GPS coordinates as formatted string
     */
    public String getGpsFormatted() {
        if (gpsLatitude == null || gpsLongitude == null) {
            return "Not available";
        }
        return String.format("%.6f, %.6f", gpsLatitude, gpsLongitude);
    }
    
    /**
     * Get camera info as formatted string
     */
    public String getCameraFormatted() {
        if (cameraMake == null && cameraModel == null) {
            return "Not available";
        }
        
        if (cameraMake != null && cameraModel != null) {
            return cameraMake + " " + cameraModel;
        } else if (cameraMake != null) {
            return cameraMake;
        } else {
            return cameraModel;
        }
    }
    
    /**
     * Check if GPS data is available
     */
    public boolean hasGpsData() {
        return gpsLatitude != null && gpsLongitude != null;
    }
    
    /**
     * Check if camera data is available
     */
    public boolean hasCameraData() {
        return cameraMake != null || cameraModel != null;
    }
    
    /**
     * Check if EXIF data is available
     */
    public boolean hasExifData() {
        return hasGpsData() || hasCameraData() || dateTaken != null || focalLength != null;
    }
}
