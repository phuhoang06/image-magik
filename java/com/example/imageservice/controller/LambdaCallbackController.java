package com.example.imageservice.controller;

import com.example.imageservice.dto.LambdaCallbackRequest;
import com.example.imageservice.entity.UploadItem;
import com.example.imageservice.entity.UploadJob;
import com.example.imageservice.entity.MockupJob;
import com.example.imageservice.entity.BatchMockupJob;
import com.example.imageservice.entity.DesignExtractionJob;
import com.example.imageservice.repository.UploadItemRepository;
import com.example.imageservice.repository.UploadJobRepository;
import com.example.imageservice.repository.MockupJobRepository;
import com.example.imageservice.repository.BatchMockupJobRepository;
import com.example.imageservice.repository.DesignExtractionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;

import java.time.LocalDateTime;
import java.util.List;
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
    private final DesignExtractionJobRepository designExtractionJobRepository;
    
    @Value("${lambda.callback.secret}")
    private String expectedSecret;
    
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
    public ResponseEntity<?> handleLambdaCallback(
            @RequestHeader("X-Worker-Secret") String workerSecret,
            @RequestBody LambdaCallbackRequest callback) {
        
        // Verify worker secret
        if (!expectedSecret.equals(workerSecret)) {
            log.error("❌ Invalid worker secret: {}", workerSecret);
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "error", "Invalid worker secret"
            ));
        }
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
     * Xử lý callback cho single mockup job hoặc design extraction job
     */
    private ResponseEntity<?> handleSingleMockupCallback(LambdaCallbackRequest callback) {
        String jobId = callback.getJobId();
        
        log.info("Processing single job callback: jobId={}, status={}", jobId, callback.getStatus());
        
        // Thử tìm DesignExtractionJob trước (vì đây là Sprint 1)
        DesignExtractionJob designJob = designExtractionJobRepository.findByJobId(jobId);
        if (designJob != null) {
            log.info("Found DesignExtractionJob: jobId={}", jobId);
            return handleDesignExtractionCallback(callback, designJob);
        }
        
        // Nếu không tìm thấy DesignExtractionJob, thử tìm MockupJob
        MockupJob mockupJob = null;
        try {
            UUID jobUuid = UUID.fromString(jobId);
            log.debug("Searching for MockupJob with UUID: {}", jobUuid);
            
            // Kiểm tra xem có job nào trong database không
            long totalJobs = mockupJobRepository.count();
            log.debug("Total MockupJobs in database: {}", totalJobs);
            
            // Tìm job theo UUID
            mockupJob = mockupJobRepository.findByJobId(jobUuid).orElse(null);
            if (mockupJob == null) {
                log.warn("Neither DesignExtractionJob nor MockupJob found: jobId={}", jobId);
                
                // Thêm thông tin debug: tìm kiếm gần đúng
                List<MockupJob> recentJobs = mockupJobRepository.findAll().stream()
                        .filter(job -> job.getJobId().toString().contains(jobId.substring(0, 8)))
                        .limit(5)
                        .collect(java.util.stream.Collectors.toList());
                
                if (!recentJobs.isEmpty()) {
                    log.warn("Found similar MockupJob IDs: {}", 
                            recentJobs.stream()
                                    .map(job -> job.getJobId().toString())
                                    .collect(java.util.stream.Collectors.toList()));
                }
                
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Job not found", 
                        "jobId", jobId,
                        "searchedTypes", "DesignExtractionJob, MockupJob"
                ));
            }
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for jobId: {}", jobId, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid jobId format", 
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
     * Xử lý callback cho design extraction job
     */
    private ResponseEntity<?> handleDesignExtractionCallback(LambdaCallbackRequest callback, DesignExtractionJob designJob) {
        String jobId = callback.getJobId();
        
        log.info("Processing design extraction callback: jobId={}, status={}", jobId, callback.getStatus());
        
        if ("COMPLETED".equals(callback.getStatus())) {
            designJob.setStatus("COMPLETED");
            designJob.setDesignUrl(callback.getResultUrl());
            designJob.setError(null);
            designJob.setProcessedAt(LocalDateTime.now());
        } else if ("FAILED".equals(callback.getStatus())) {
            designJob.setStatus("FAILED");
            designJob.setError(callback.getErrorMessage());
            designJob.setProcessedAt(LocalDateTime.now());
        }
        
        designExtractionJobRepository.save(designJob);
        log.info("DesignExtractionJob {} updated status: {}", jobId, callback.getStatus());
        
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Design extraction callback processed successfully",
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
     * Endpoint để debug - kiểm tra trạng thái database
     */
    @GetMapping("/debug/status")
    public ResponseEntity<?> getDebugStatus() {
        try {
            long totalMockupJobs = mockupJobRepository.count();
            long totalBatchMockupJobs = batchMockupJobRepository.count();
            long totalUploadJobs = uploadJobRepository.count();
            long totalUploadItems = uploadItemRepository.count();
            
            return ResponseEntity.ok(Map.of(
                    "totalMockupJobs", totalMockupJobs,
                    "totalBatchMockupJobs", totalBatchMockupJobs,
                    "totalUploadJobs", totalUploadJobs,
                    "totalUploadItems", totalUploadItems,
                    "timestamp", LocalDateTime.now()
            ));
        } catch (Exception e) {
            log.error("Error getting debug status", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to get debug status",
                    "message", e.getMessage()
            ));
        }
    }
    
    /**
     * Endpoint để debug - tạo test MockupJob
     */
    @PostMapping("/debug/create-test-mockup")
    public ResponseEntity<?> createTestMockupJob() {
        try {
            // Tạo test MockupJob
            MockupJob testJob = new MockupJob();
            testJob.setJobId(UUID.randomUUID());
            testJob.setBackgroundUrl("https://example.com/background.jpg");
            testJob.setDesignUrl("https://example.com/design.jpg");
            testJob.setStatus(MockupJob.JobStatus.PENDING);
            testJob.setUserId("debug-user");
            
            log.info("Creating test MockupJob: jobId={}", testJob.getJobId());
            MockupJob savedJob = mockupJobRepository.save(testJob);
            log.info("Test MockupJob saved: jobId={}", savedJob.getJobId());
            
            // Verify
            MockupJob verifyJob = mockupJobRepository.findByJobId(testJob.getJobId()).orElse(null);
            if (verifyJob == null) {
                log.error("CRITICAL: Test MockupJob not found after save!");
                return ResponseEntity.status(500).body(Map.of(
                        "error", "Test MockupJob not found after save",
                        "jobId", testJob.getJobId()
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "jobId", savedJob.getJobId(),
                    "status", savedJob.getStatus(),
                    "message", "Test MockupJob created and verified successfully"
            ));
        } catch (Exception e) {
            log.error("Error creating test MockupJob", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to create test MockupJob",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Endpoint để debug - tìm MockupJob theo jobId
     */
    @GetMapping("/debug/mockup-job/{jobId}")
    public ResponseEntity<?> getMockupJobDebug(@PathVariable String jobId) {
        try {
            UUID jobUuid = UUID.fromString(jobId);
            MockupJob job = mockupJobRepository.findByJobId(jobUuid).orElse(null);
            
            if (job == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(Map.of(
                    "jobId", job.getJobId(),
                    "status", job.getStatus(),
                    "backgroundUrl", job.getBackgroundUrl(),
                    "designUrl", job.getDesignUrl(),
                    "userId", job.getUserId(),
                    "createdAt", job.getCreatedAt(),
                    "updatedAt", job.getUpdatedAt(),
                    "resultUrl", job.getResultUrl(),
                    "errorMessage", job.getErrorMessage()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid UUID format",
                    "jobId", jobId
            ));
        } catch (Exception e) {
            log.error("Error getting mockup job debug info", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to get mockup job debug info",
                    "message", e.getMessage()
            ));
        }
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