package com.example.imageservice.service;

import com.example.imageservice.dto.MockupRequestDto;
import com.example.imageservice.dto.BatchMockupRequestDto;
import com.example.imageservice.entity.BatchMockupJob;
import com.example.imageservice.repository.BatchMockupJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BatchMockupService {
    private static final Logger log = LoggerFactory.getLogger(BatchMockupService.class);

    private final BatchMockupJobRepository batchMockupJobRepository;
    private final LambdaService lambdaService;
    private final ObjectMapper objectMapper;

    public BatchMockupService(BatchMockupJobRepository batchMockupJobRepository,
                              LambdaService lambdaService,
                              ObjectMapper objectMapper) {
        this.batchMockupJobRepository = batchMockupJobRepository;
        this.lambdaService = lambdaService;
        this.objectMapper = objectMapper;
    }
    
    @Transactional
    public String createBatchMockupJobs(List<BatchMockupRequestDto> requests, String userId) {
        String batchJobId = UUID.randomUUID().toString();
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        
        log.info("Creating batch mockup jobs with batchJobId: {} for user: {}", batchJobId, userId);
        
        for (int i = 0; i < requests.size(); i++) {
            BatchMockupRequestDto request = requests.get(i);
            
            // Validate required fields for each request
            if (request.getBackgroundUrl() == null || request.getBackgroundUrl().trim().isEmpty()) {
                throw new IllegalArgumentException("Background URL is required and cannot be null or empty for request at index " + i);
            }
            if (request.getDesignUrl() == null || request.getDesignUrl().trim().isEmpty()) {
                throw new IllegalArgumentException("Design URL is required and cannot be null or empty for request at index " + i);
            }
            
            String itemId = UUID.randomUUID().toString();
            
            // Convert advanced options to JSON strings
            String removeBackgroundJson = null;
            String warpJson = null;
            String displaceJson = null;
            
            try {
                if (request.getRemoveBackground() != null) {
                    removeBackgroundJson = objectMapper.writeValueAsString(request.getRemoveBackground());
                }
                if (request.getWarp() != null) {
                    warpJson = objectMapper.writeValueAsString(request.getWarp());
                }
                if (request.getDisplace() != null) {
                    displaceJson = objectMapper.writeValueAsString(request.getDisplace());
                }
            } catch (Exception e) {
                log.warn("Failed to serialize advanced options for item {}: {}", itemId, e.getMessage());
            }
            
            BatchMockupJob batchMockupJob = new BatchMockupJob();
            batchMockupJob.setBatchJobId(batchJobId);
            batchMockupJob.setItemId(itemId);
            batchMockupJob.setBackgroundUrl(request.getBackgroundUrl().trim());
            batchMockupJob.setDesignUrl(request.getDesignUrl().trim());
            batchMockupJob.setStatus(BatchMockupJob.JobStatus.SENT_TO_LAMBDA);
            batchMockupJob.setDesignX(request.getDesignX());
            batchMockupJob.setDesignY(request.getDesignY());
            batchMockupJob.setDesignScale(request.getDesignScale() == null ? 1.0 : request.getDesignScale());
            batchMockupJob.setDesignRotation(request.getDesignRotation() == null ? 0.0 : request.getDesignRotation());
            batchMockupJob.setOutputFormat(request.getOutputFormat());
            batchMockupJob.setOutputQuality(request.getOutputQuality());
            batchMockupJob.setOpacity(request.getOpacity());
            batchMockupJob.setUserId(userId);
            batchMockupJob.setRemoveBackgroundOptions(removeBackgroundJson);
            batchMockupJob.setWarpOptions(warpJson);
            batchMockupJob.setDisplaceOptions(displaceJson);
            
            batchMockupJobRepository.save(batchMockupJob);
            log.info("✅ Saved BatchMockupJob to database: batchJobId={}, itemId={}", batchJobId, itemId);
            
            // Create item for Lambda processing - include an "options" object so worker receives unified options
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("itemId", itemId);
            item.put("backgroundUrl", request.getBackgroundUrl());
            item.put("designUrl", request.getDesignUrl());

            Map<String, Object> options = new java.util.HashMap<>();
            options.put("designX", request.getDesignX());
            options.put("designY", request.getDesignY());
            options.put("designScale", request.getDesignScale());
            options.put("designRotation", request.getDesignRotation());
            options.put("outputFormat", request.getOutputFormat());
            options.put("outputQuality", request.getOutputQuality());
            options.put("opacity", request.getOpacity());

            if (request.getRemoveBackground() != null) {
                options.put("removeBackground", request.getRemoveBackground());
            }
            if (request.getWarp() != null) {
                options.put("warp", request.getWarp());
            }
            if (request.getDisplace() != null) {
                options.put("displace", request.getDisplace());
            }

            item.put("options", options);
            items.add(item);
            
            log.info("[BATCH MOCKUP] Saved job: batchJobId={}, itemId={}, backgroundUrl={}, designUrl={}", 
                    batchJobId, itemId, request.getBackgroundUrl(), request.getDesignUrl());
        }
        
        // Gửi batch sang Lambda xử lý sau khi transaction commit
        log.info("🚀 Scheduling batch send to Lambda after transaction commit: batchJobId={}, requests={}", batchJobId, items.size());
        
        // Đảm bảo gửi sang Lambda sau khi transaction commit
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("✅ Transaction committed, now sending batch to Lambda: batchJobId={}", batchJobId);
                lambdaService.invokeBatchMockupProcessorAsync(batchJobId, items);
            }
        });
        
        log.info("✅ Batch mockup jobs created successfully: {} items for batchJobId: {}", items.size(), batchJobId);
        return batchJobId;
    }
    
    public List<BatchMockupJob> getBatchMockupJobs(String batchJobId) {
        return batchMockupJobRepository.findByBatchJobId(batchJobId);
    }
    
    public BatchMockupJob getBatchMockupJob(String batchJobId, String itemId) {
        return batchMockupJobRepository.findByBatchJobIdAndItemId(batchJobId, itemId)
                .orElseThrow(() -> new RuntimeException("Batch mockup job not found: " + batchJobId + ":" + itemId));
    }
    
    public List<BatchMockupJob> getBatchMockupJobsByUserId(String userId) {
        return batchMockupJobRepository.findByUserId(userId);
    }
    
    public void updateBatchMockupJobStatus(String batchJobId, String itemId, BatchMockupJob.JobStatus status, String resultUrl, String errorMessage) {
        BatchMockupJob job = getBatchMockupJob(batchJobId, itemId);
        job.setStatus(status);
        if (resultUrl != null) {
            job.setResultUrl(resultUrl);
        }
        if (errorMessage != null) {
            job.setErrorMessage(errorMessage);
        }
        batchMockupJobRepository.save(job);
        
        log.info("Updated batch mockup job status: batchJobId={}, itemId={}, status={}", batchJobId, itemId, status);
    }
}

