package com.example.imageservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mockup_jobs")
public class MockupJob {

    @Id
    @Column(name = "job_id", updatable = false, nullable = false)
    private UUID jobId;

    @Column(name = "background_url")
    private String backgroundUrl;

    @Column(name = "design_url")
    private String designUrl;

    @Column(name = "design_x")
    private Integer designX;

    @Column(name = "design_y")
    private Integer designY;

    @Column(name = "design_scale")
    private Double designScale;

    @Column(name = "design_rotation")
    private Double designRotation;

    @Column(name = "output_format")
    private String outputFormat;

    @Column(name = "output_quality")
    private Integer outputQuality;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "result_url")
    private String resultUrl;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "opacity")
    private Integer opacity;

    public enum JobStatus {
        PENDING,
        PROCESSING,
        SENT_TO_LAMBDA,
        COMPLETED,
        FAILED
    }
}


