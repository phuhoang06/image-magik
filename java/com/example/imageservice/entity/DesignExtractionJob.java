package com.example.imageservice.entity;

import com.example.imageservice.converter.JsonConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity for Design Extraction Jobs
 */
@Entity
@Table(name = "design_extraction_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesignExtractionJob {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "job_id", unique = true, nullable = false)
    private String jobId;
    
    @Column(name = "status", nullable = false)
    private String status; // PROCESSING, COMPLETED, FAILED
    
    @Column(name = "design_url")
    private String designUrl;
    
    @Column(name = "design_id")
    private String designId;
    
    @Column(name = "confidence")
    private Double confidence;
    
    @Column(name = "error_message", columnDefinition = "CLOB")
    private String error;
    
    @Column(name = "options", columnDefinition = "CLOB")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> options;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "mockup_url")
    private String mockupUrl;
    
    @Column(name = "callback_url")
    private String callbackUrl;
    
    @Column(name = "worker_secret")
    private String workerSecret;
}
