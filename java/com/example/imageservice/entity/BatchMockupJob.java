package com.example.imageservice.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "batch_mockup_jobs_v2", uniqueConstraints = @UniqueConstraint(columnNames = {"batchJobId", "itemId"}))
public class BatchMockupJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String batchJobId;

    @Column(nullable = false)
    private String backgroundUrl;

    @Column(nullable = false)
    private String designUrl;

    @Column
    private String itemId; // ID của item trong batch

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column
    private String resultUrl;

    @Column
    private String errorMessage;

    @Column
    private Integer backgroundWidth;

    @Column
    private Integer backgroundHeight;

    @Column
    private Integer designWidth;

    @Column
    private Integer designHeight;

    @Column
    private Integer designX;

    @Column
    private Integer designY;

    @Column
    private Double designScale;

    @Column
    private Double designRotation;

    @Column
    private String outputFormat;

    @Column
    private Integer outputQuality;

    @Column
    private Integer opacity;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private String userId;

    // Advanced ImageMagick features - stored as JSON
    @Column(columnDefinition = "CLOB")
    private String removeBackgroundOptions;

    @Column(columnDefinition = "CLOB")
    private String warpOptions;

    @Column(columnDefinition = "CLOB")
    private String displaceOptions;

    public BatchMockupJob() {}

    // Getters and setters (generated manually)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBatchJobId() { return batchJobId; }
    public void setBatchJobId(String batchJobId) { this.batchJobId = batchJobId; }
    public String getBackgroundUrl() { return backgroundUrl; }
    public void setBackgroundUrl(String backgroundUrl) { this.backgroundUrl = backgroundUrl; }
    public String getDesignUrl() { return designUrl; }
    public void setDesignUrl(String designUrl) { this.designUrl = designUrl; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public String getResultUrl() { return resultUrl; }
    public void setResultUrl(String resultUrl) { this.resultUrl = resultUrl; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getBackgroundWidth() { return backgroundWidth; }
    public void setBackgroundWidth(Integer backgroundWidth) { this.backgroundWidth = backgroundWidth; }
    public Integer getBackgroundHeight() { return backgroundHeight; }
    public void setBackgroundHeight(Integer backgroundHeight) { this.backgroundHeight = backgroundHeight; }
    public Integer getDesignWidth() { return designWidth; }
    public void setDesignWidth(Integer designWidth) { this.designWidth = designWidth; }
    public Integer getDesignHeight() { return designHeight; }
    public void setDesignHeight(Integer designHeight) { this.designHeight = designHeight; }
    public Integer getDesignX() { return designX; }
    public void setDesignX(Integer designX) { this.designX = designX; }
    public Integer getDesignY() { return designY; }
    public void setDesignY(Integer designY) { this.designY = designY; }
    public Double getDesignScale() { return designScale; }
    public void setDesignScale(Double designScale) { this.designScale = designScale; }
    public Double getDesignRotation() { return designRotation; }
    public void setDesignRotation(Double designRotation) { this.designRotation = designRotation; }
    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
    public Integer getOutputQuality() { return outputQuality; }
    public void setOutputQuality(Integer outputQuality) { this.outputQuality = outputQuality; }
    public Integer getOpacity() { return opacity; }
    public void setOpacity(Integer opacity) { this.opacity = opacity; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRemoveBackgroundOptions() { return removeBackgroundOptions; }
    public void setRemoveBackgroundOptions(String removeBackgroundOptions) { this.removeBackgroundOptions = removeBackgroundOptions; }
    public String getWarpOptions() { return warpOptions; }
    public void setWarpOptions(String warpOptions) { this.warpOptions = warpOptions; }
    public String getDisplaceOptions() { return displaceOptions; }
    public void setDisplaceOptions(String displaceOptions) { this.displaceOptions = displaceOptions; }

    public enum JobStatus {
        PENDING,
        PROCESSING,
        SENT_TO_LAMBDA,
        COMPLETED,
        FAILED
    }
}
