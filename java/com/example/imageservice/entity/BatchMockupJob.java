package com.example.imageservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

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


    public enum JobStatus {
        PENDING,
        PROCESSING,
        SENT_TO_LAMBDA,
        COMPLETED,
        FAILED
    }
}
