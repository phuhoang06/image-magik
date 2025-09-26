package com.example.imageservice.service;

import com.example.imageservice.dto.MockupRequestDto;
import com.example.imageservice.entity.MockupJob;
import com.example.imageservice.repository.MockupJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class MockupService {

    private static final Logger log = LoggerFactory.getLogger(MockupService.class);

    private final MockupJobRepository mockupJobRepository;
    private final LambdaService lambdaService;

    public MockupService(MockupJobRepository mockupJobRepository, LambdaService lambdaService) {
        this.mockupJobRepository = mockupJobRepository;
        this.lambdaService = lambdaService;
    }

    @Transactional
    public MockupJob createMockupJob(MockupRequestDto request, String userId) {
        log.info("Creating mockup job - backgroundUrl: '{}', designUrl: '{}'",
                request.getBackgroundUrl(), request.getDesignUrl());

        // Validate required fields
        if (request.getBackgroundUrl() == null || request.getBackgroundUrl().trim().isEmpty()) {
            log.error("Validation failed: Background URL is null or empty");
            throw new IllegalArgumentException("Background URL is required and cannot be null or empty");
        }
        if (request.getDesignUrl() == null || request.getDesignUrl().trim().isEmpty()) {
            log.error("Validation failed: Design URL is null or empty");
            throw new IllegalArgumentException("Design URL is required and cannot be null or empty");
        }

        log.info("Validation passed, proceeding with mockup job creation");

        UUID jobId = UUID.randomUUID();

    MockupJob mockupJob = new MockupJob();
    mockupJob.setJobId(jobId);
    mockupJob.setBackgroundUrl(request.getBackgroundUrl().trim());
    mockupJob.setDesignUrl(request.getDesignUrl().trim());
    mockupJob.setStatus(MockupJob.JobStatus.PENDING);
    mockupJob.setDesignX(request.getDesignX());
    mockupJob.setDesignY(request.getDesignY());
    mockupJob.setDesignScale(request.getDesignScale() == null ? Double.valueOf(1.0) : request.getDesignScale());
    mockupJob.setDesignRotation(request.getDesignRotation() == null ? Double.valueOf(0.0) : request.getDesignRotation());
    mockupJob.setOutputFormat(request.getOutputFormat());
    mockupJob.setOutputQuality(request.getOutputQuality());
    mockupJob.setOpacity(request.getOpacity());
    mockupJob.setUserId(userId);

        // Luôn gửi mockup jobs đến Lambda để xử lý toàn bộ pipeline
        mockupJob.setStatus(MockupJob.JobStatus.SENT_TO_LAMBDA);
        
        log.info("Saving MockupJob to database: jobId={}, status={}", jobId, mockupJob.getStatus());
        MockupJob savedJob = mockupJobRepository.save(mockupJob);
        log.info("MockupJob saved successfully: jobId={}, id={}", savedJob.getJobId(), savedJob.getJobId());
        
        // Verify job was saved correctly
        MockupJob verifyJob = mockupJobRepository.findByJobId(jobId).orElse(null);
        if (verifyJob == null) {
            log.error("CRITICAL: MockupJob not found after save! jobId={}", jobId);
            throw new RuntimeException("Failed to save MockupJob to database");
        } else {
            log.info("MockupJob verification successful: jobId={}", verifyJob.getJobId());
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Sending MockupJob to Lambda: jobId={}", savedJob.getJobId());
                lambdaService.sendMockupJobToLambda(savedJob, request);
                log.info("MockupJob sent to Lambda successfully: jobId={}", savedJob.getJobId());
            } catch (Exception e) {
                log.error("Failed to send MockupJob to Lambda: jobId={}", savedJob.getJobId(), e);
            }
        });
        
        return savedJob;
    }

    public MockupJob getMockupJob(UUID jobId) {
        return mockupJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new RuntimeException("Mockup job not found: " + jobId));
    }

    public MockupJob getMockupJobByCompositeId(String batchJobId, MockupRequestDto request) {
        // Tìm theo jobId bắt đầu bằng batchJobId và các trường unique của request
        // Đơn giản nhất: tìm jobId bắt đầu bằng batchJobId
        return mockupJobRepository.findByJobIdStartingWith(batchJobId + ":")
                .stream()
                .filter(j -> j.getBackgroundUrl().equals(request.getBackgroundUrl())
                        && j.getDesignUrl().equals(request.getDesignUrl())
                        && j.getDesignX().equals(request.getDesignX())
                        && j.getDesignY().equals(request.getDesignY()))
                .findFirst().orElse(null);
    }

    // Batch logic đã được chuyển sang BatchMockupService riêng
}
