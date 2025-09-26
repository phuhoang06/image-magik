package com.example.imageservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Design Similarity Entity
 * Represents similarity relationships between designs
 */
@Entity
@Table(name = "design_similarities", indexes = {
    @Index(name = "idx_similarity_design1", columnList = "design1Id"),
    @Index(name = "idx_similarity_design2", columnList = "design2Id"),
    @Index(name = "idx_similarity_score", columnList = "similarityScore"),
    @Index(name = "idx_similarity_created_at", columnList = "createdAt")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesignSimilarity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "design1_id", nullable = false, length = 36)
    private String design1Id;

    @Column(name = "design2_id", nullable = false, length = 36)
    private String design2Id;

    @Column(name = "similarity_score", nullable = false)
    private Double similarityScore;

    @Column(name = "similarity_type", length = 50)
    @Enumerated(EnumType.STRING)
    private SimilarityType similarityType;

    @Column(name = "algorithm_used", length = 100)
    private String algorithmUsed;

    @Column(name = "confidence_level")
    private Double confidenceLevel;

    @Column(name = "is_verified")
    private Boolean isVerified;

    @Column(name = "verified_by", length = 100)
    private String verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (isVerified == null) {
            isVerified = false;
        }
        if (similarityType == null) {
            similarityType = SimilarityType.VISUAL;
        }
    }

    /**
     * Similarity Type Enum
     */
    public enum SimilarityType {
        VISUAL,        // Visual similarity based on embeddings
        STRUCTURAL,    // Structural similarity (layout, composition)
        COLOR,         // Color-based similarity
        TEXTURE,       // Texture-based similarity
        SEMANTIC,      // Semantic similarity (meaning, concept)
        HYBRID         // Combination of multiple similarity types
    }

    /**
     * Get similarity level based on score
     */
    public String getSimilarityLevel() {
        if (similarityScore >= 0.9) {
            return "VERY_HIGH";
        } else if (similarityScore >= 0.8) {
            return "HIGH";
        } else if (similarityScore >= 0.7) {
            return "MEDIUM";
        } else if (similarityScore >= 0.6) {
            return "LOW";
        } else {
            return "VERY_LOW";
        }
    }

    /**
     * Check if similarity is significant (above threshold)
     */
    public boolean isSignificant(double threshold) {
        return similarityScore >= threshold;
    }

    /**
     * Check if this is a potential duplicate
     */
    public boolean isPotentialDuplicate() {
        return similarityScore >= 0.85 && confidenceLevel != null && confidenceLevel >= 0.8;
    }

    /**
     * Mark as verified
     */
    public void markAsVerified(String verifiedBy) {
        this.isVerified = true;
        this.verifiedBy = verifiedBy;
        this.verifiedAt = LocalDateTime.now();
    }

    /**
     * Mark as unverified
     */
    public void markAsUnverified() {
        this.isVerified = false;
        this.verifiedBy = null;
        this.verifiedAt = null;
    }
}
