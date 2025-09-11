package com.example.imageservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "UPLOAD_JOBS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadJob {
    
    @Id
    @Column(name = "JOB_ID", length = 36)
    private String jobId;
    
    @Column(name = "USER_ID", nullable = false, length = 255)
    private String userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private JobStatus status;
    
    @CreationTimestamp
    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "FINISHED_AT")
    private LocalDateTime finishedAt;
    
    @UpdateTimestamp
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
    
    @Column(name = "TOTAL_ITEMS", nullable = false)
    private Integer total;
    
    @Column(name = "COMPLETED_ITEMS", nullable = false)
    @Builder.Default
    private Integer completed = 0;
    
    @Column(name = "FAILED_ITEMS", nullable = false)
    @Builder.Default
    private Integer failed = 0;
    
    @Column(name = "PROCESSING_TIME_SECONDS")
    private Long processingTimeSeconds;
    
    @Column(name = "LAMBDA_REQUEST_ID", length = 100)
    private String lambdaRequestId;
    
    @Column(name = "ERROR_MESSAGE", length = 1000)
    private String errorMessage;
    
    @OneToMany(mappedBy = "uploadJob", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<UploadItem> items = new ArrayList<>();
    
    public enum JobStatus {
        PENDING,
        SENT_TO_LAMBDA,
        IN_PROGRESS,
        COMPLETED,
        PARTIALLY_COMPLETED,
        FAILED
    }
    
    public void calculateProcessingTime() {
        if (createdAt != null && finishedAt != null) {
            this.processingTimeSeconds = java.time.Duration.between(createdAt, finishedAt).getSeconds();
        }
    }
    
    public void incrementCompleted() {
        this.completed = (this.completed == null ? 0 : this.completed) + 1;
    }
    
    public void incrementFailed() {
        this.failed = (this.failed == null ? 0 : this.failed) + 1;
    }
}