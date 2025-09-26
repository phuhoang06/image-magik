package com.example.imageservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Design Entity
 * Represents an extracted design from a mockup image
 */
@Entity
@Table(name = "designs", indexes = {
    @Index(name = "idx_design_user_id", columnList = "userId"),
    @Index(name = "idx_design_category", columnList = "category"),
    @Index(name = "idx_design_created_at", columnList = "createdAt"),
    @Index(name = "idx_design_quality_score", columnList = "qualityScore")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Design {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "original_mockup_id", length = 36)
    private String originalMockupId;

    @Column(name = "design_name", length = 255)
    private String designName;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "subcategory", length = 100)
    private String subcategory;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "s3_key", length = 500)
    private String s3Key;

    @Column(name = "s3_bucket", length = 100)
    private String s3Bucket;

    @Column(name = "file_format", length = 10)
    private String fileFormat;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "x_coordinate")
    private Integer xCoordinate;

    @Column(name = "y_coordinate")
    private Integer yCoordinate;

    @Column(name = "detection_confidence")
    private Double detectionConfidence;

    @Column(name = "clip_confidence")
    private Double clipConfidence;

    @Column(name = "quality_score")
    private Double qualityScore;

    @Column(name = "quality_level", length = 20)
    private String qualityLevel;

    @Column(name = "embedding_vector", columnDefinition = "CLOB")
    private String embeddingVector; // JSON string of float array

    @Column(name = "metadata", columnDefinition = "CLOB")
    private String metadata; // JSON string of additional metadata

    @Column(name = "tags", length = 1000)
    private String tags; // Comma-separated tags

    @Column(name = "is_public")
    private Boolean isPublic;

    @Column(name = "download_count")
    private Integer downloadCount;

    @Column(name = "view_count")
    private Integer viewCount;

    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    private DesignStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (downloadCount == null) {
            downloadCount = 0;
        }
        if (viewCount == null) {
            viewCount = 0;
        }
        if (isPublic == null) {
            isPublic = false;
        }
        if (status == null) {
            status = DesignStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Design Status Enum
     */
    public enum DesignStatus {
        ACTIVE,
        INACTIVE,
        DELETED,
        PROCESSING,
        FAILED
    }

    /**
     * Get embedding vector as float array
     */
    public float[] getEmbeddingVectorAsArray() {
        if (embeddingVector == null || embeddingVector.isEmpty()) {
            return new float[0];
        }
        try {
            // Simple parsing - in real implementation, use proper JSON parsing
            String[] parts = embeddingVector.replace("[", "").replace("]", "").split(",");
            float[] result = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Float.parseFloat(parts[i].trim());
            }
            return result;
        } catch (Exception e) {
            return new float[0];
        }
    }

    /**
     * Set embedding vector from float array
     */
    public void setEmbeddingVectorFromArray(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            this.embeddingVector = null;
            return;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        this.embeddingVector = sb.toString();
    }

    /**
     * Get tags as array
     */
    public String[] getTagsAsArray() {
        if (tags == null || tags.isEmpty()) {
            return new String[0];
        }
        return tags.split(",");
    }

    /**
     * Set tags from array
     */
    public void setTagsFromArray(String[] tagArray) {
        if (tagArray == null || tagArray.length == 0) {
            this.tags = null;
            return;
        }
        this.tags = String.join(",", tagArray);
    }

    /**
     * Increment download count
     */
    public void incrementDownloadCount() {
        if (downloadCount == null) {
            downloadCount = 0;
        }
        downloadCount++;
    }

    /**
     * Increment view count
     */
    public void incrementViewCount() {
        if (viewCount == null) {
            viewCount = 0;
        }
        viewCount++;
    }
}
