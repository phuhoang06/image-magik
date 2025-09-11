package com.example.imageservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "UPLOAD_ITEMS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadItem {
    
    @Id
    @Column(name = "ITEM_ID", length = 36)
    private String itemId;
    
    @Column(name = "ORIGINAL_URL", nullable = false, length = 2048)
    private String originalUrl;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    @Builder.Default
    private ItemStatus status = ItemStatus.QUEUED;
    
    @Column(name = "CDN_URL", length = 2048)
    private String cdnUrl;
    
    @Column(name = "S3_KEY", length = 1024)
    private String s3Key;
    
    @Column(name = "SIZE_BYTES")
    private Long sizeBytes;
    
    @Column(name = "WIDTH")
    private Integer width;
    
    @Column(name = "HEIGHT")
    private Integer height;
    
    // Metadata mới
    @Column(name = "DPI_X")
    private Integer dpiX;
    
    @Column(name = "DPI_Y")
    private Integer dpiY;
    
    @Column(name = "FORMAT", length = 20)
    private String format;
    
    @Column(name = "COLOR_SPACE", length = 50)
    private String colorSpace;
    
    @Column(name = "CHANNELS")
    private Integer channels;
    
    @Column(name = "HAS_ALPHA")
    private Boolean hasAlpha;
    
    @Column(name = "IS_OPAQUE")
    private Boolean isOpaque;
    
    @Column(name = "ORIENTATION")
    private Integer orientation;
    
    @Column(name = "PROFILE_NAME", length = 100)
    private String profileName;
    
    @Column(name = "COMPRESSION", length = 50)
    private String compression;
    
    @Column(name = "ERROR_MESSAGE", length = 1000)
    private String errorMessage;
    
    @CreationTimestamp
    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
    
    @Column(name = "PROCESSED_AT")
    private LocalDateTime processedAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "JOB_ID", nullable = false)
    @JsonIgnore
    private UploadJob uploadJob;
    
    /**
     * Lấy jobId từ relationship
     */
    public String getJobId() {
        return uploadJob != null ? uploadJob.getJobId() : null;
    }
    
    /**
     * Lấy userId từ relationship
     */
    public String getUserId() {
        return uploadJob != null ? uploadJob.getUserId() : null;
    }
    
    public enum ItemStatus {
        QUEUED,
        SENT_TO_LAMBDA,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}