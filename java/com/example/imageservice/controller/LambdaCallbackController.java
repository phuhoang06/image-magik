package com.example.imageservice.controller;

import com.example.imageservice.dto.LambdaCallbackRequest;
import com.example.imageservice.entity.UploadItem;
import com.example.imageservice.entity.UploadJob;
import com.example.imageservice.entity.MockupJob;
import com.example.imageservice.entity.BatchMockupJob;
import com.example.imageservice.repository.UploadItemRepository;
import com.example.imageservice.repository.UploadJobRepository;
import com.example.imageservice.repository.MockupJobRepository;
import com.example.imageservice.repository.BatchMockupJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lambda")
@RequiredArgsConstructor
@Slf4j
public class LambdaCallbackController {
    
    private final UploadItemRepository uploadItemRepository;
    private final UploadJobRepository uploadJobRepository;
    private final MockupJobRepository mockupJobRepository;
    private final BatchMockupJobRepository batchMockupJobRepository;
    
    /**
     * Handle CORS preflight for Lambda callback
     * OPTIONS /api/v1/lambda/callback
     */
    @RequestMapping(value = "/callback", method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions() {
        return ResponseEntity.ok()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "POST, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, X-Worker-Secret")
                .build();
    }

    /**
     * Nhận callback từ Lambda sau khi xử lý ảnh hoặc mockup
     * POST /api/v1/lambda/callback
     */
    @PostMapping("/callback")
    public ResponseEntity<?> handleLambdaCallback(@RequestBody LambdaCallbackRequest callback) {
        log.info("Received Lambda callback: jobId={}, itemId={}, status={}", 
                callback.getJobId(), callback.getItemId(), callback.getStatus());
        
        try {
            // Xử lý batch mockup job (có cả jobId và itemId)
            if (callback.getJobId() != null && callback.getItemId() != null) {
                return handleBatchMockupCallback(callback);
            }
            
            // Xử lý single mockup job (chỉ có jobId, không có itemId)
            if (callback.getJobId() != null && callback.getItemId() == null) {
                return handleSingleMockupCallback(callback);
            }
            
            // Xử lý upload item (chỉ có itemId, không có jobId)
            if (callback.getJobId() == null && callback.getItemId() != null) {
                return handleUploadItemCallback(callback);
            }
            
            log.warn("Invalid callback format: jobId={}, itemId={}", callback.getJobId(), callback.getItemId());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid callback format", 
                    "message", "Either jobId+itemId (batch), jobId only (single), or itemId only (upload) required"
            ));
            
        } catch (Exception e) {
            log.error("Error processing callback: jobId={}, itemId={}", callback.getJobId(), callback.getItemId(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Internal server error",
                    "message", e.getMessage()
            ));
        }
    }
    
    /**
     * Xử lý callback cho batch mockup job
     */
    private ResponseEntity<?> handleBatchMockupCallback(LambdaCallbackRequest callback) {
        String batchJobId = callback.getJobId();
        String itemId = callback.getItemId();
        
        log.info("Processing batch mockup callback: batchJobId={}, itemId={}, status={}", 
                batchJobId, itemId, callback.getStatus());
        
        BatchMockupJob batchMockupJob = batchMockupJobRepository.findByBatchJobIdAndItemId(batchJobId, itemId).orElse(null);
        if (batchMockupJob == null) {
            log.warn("BatchMockupJob not found: batchJobId={}, itemId={}", batchJobId, itemId);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "BatchMockupJob not found", 
                    "batchJobId", batchJobId,
                    "itemId", itemId
            ));
        }
        
        if ("COMPLETED".equals(callback.getStatus())) {
            batchMockupJob.setStatus(BatchMockupJob.JobStatus.COMPLETED);
            batchMockupJob.setResultUrl(callback.getResultUrl());
            batchMockupJob.setErrorMessage(null);
        } else if ("FAILED".equals(callback.getStatus())) {
            batchMockupJob.setStatus(BatchMockupJob.JobStatus.FAILED);
            batchMockupJob.setErrorMessage(callback.getErrorMessage());
        }
        
        batchMockupJobRepository.save(batchMockupJob);
        log.info("BatchMockupJob {}:{} updated status: {}", batchJobId, itemId, callback.getStatus());
        
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Batch mockup callback processed successfully",
                "batchJobId", batchJobId,
                "itemId", itemId
        ));
    }
    
    /**
     * Xử lý callback cho single mockup job
     */
    private ResponseEntity<?> handleSingleMockupCallback(LambdaCallbackRequest callback) {
        String jobId = callback.getJobId();
        
        log.info("Processing single mockup callback: jobId={}, status={}", jobId, callback.getStatus());
        
        MockupJob mockupJob = mockupJobRepository.findByJobId(UUID.fromString(jobId)).orElse(null);
        if (mockupJob == null) {
            log.warn("MockupJob not found: jobId={}", jobId);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "MockupJob not found", 
                    "jobId", jobId
            ));
        }
        
        if ("COMPLETED".equals(callback.getStatus())) {
            mockupJob.setStatus(MockupJob.JobStatus.COMPLETED);
            mockupJob.setResultUrl(callback.getResultUrl());
            mockupJob.setErrorMessage(null);
        } else if ("FAILED".equals(callback.getStatus())) {
            mockupJob.setStatus(MockupJob.JobStatus.FAILED);
            mockupJob.setErrorMessage(callback.getErrorMessage());
        }
        
        mockupJobRepository.save(mockupJob);
        log.info("MockupJob {} updated status: {}", jobId, callback.getStatus());
        
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Single mockup callback processed successfully",
                "jobId", jobId
        ));
    }
    
    /**
     * Xử lý callback cho upload item
     */
    private ResponseEntity<?> handleUploadItemCallback(LambdaCallbackRequest callback) {
        String itemId = callback.getItemId();
        
        log.info("Processing upload item callback: itemId={}, status={}", itemId, callback.getStatus());
        
        UploadItem item = uploadItemRepository.findById(itemId).orElse(null);
        if (item == null) {
            log.warn("UploadItem not found: itemId={}", itemId);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "UploadItem not found", 
                    "itemId", itemId
            ));
        }
        
        if ("COMPLETED".equals(callback.getStatus())) {
            item.setStatus(UploadItem.ItemStatus.COMPLETED);
            item.setCdnUrl(callback.getResultUrl());
            item.setErrorMessage(null);
        } else if ("FAILED".equals(callback.getStatus())) {
            item.setStatus(UploadItem.ItemStatus.FAILED);
            item.setErrorMessage(callback.getErrorMessage());
        }
        
        uploadItemRepository.save(item);
        log.info("UploadItem {} updated status: {}", itemId, callback.getStatus());
        
        // Update parent job status if all items are processed
        updateParentJobStatus(item.getUploadJob().getJobId());
        
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Upload item callback processed successfully",
                "itemId", itemId
        ));
    }
    
    /**
     * Cập nhật trạng thái job cha khi tất cả items đã được xử lý
     */
    private void updateParentJobStatus(String jobId) {
        UploadJob job = uploadJobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        
        long totalItems = job.getItems().size();
        long completedItems = job.getItems().stream()
                .filter(item -> item.getStatus() == UploadItem.ItemStatus.COMPLETED)
                .count();
        long failedItems = job.getItems().stream()
                .filter(item -> item.getStatus() == UploadItem.ItemStatus.FAILED)
                .count();
        
        if (completedItems + failedItems == totalItems) {
            if (completedItems == totalItems) {
                job.setStatus(UploadJob.JobStatus.COMPLETED);
            } else {
                job.setStatus(UploadJob.JobStatus.FAILED);
            }
            job.setFinishedAt(LocalDateTime.now());
            job.calculateProcessingTime();
            uploadJobRepository.save(job);
            
            log.info("UploadJob {} completed: {}/{} items successful", jobId, completedItems, totalItems);
        }
    }
}