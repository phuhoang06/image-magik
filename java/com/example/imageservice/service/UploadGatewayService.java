package com.example.imageservice.service;

import com.example.imageservice.entity.UploadJob;
import com.example.imageservice.entity.UploadItem;
import com.example.imageservice.repository.UploadJobRepository;
import com.example.imageservice.repository.UploadItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadGatewayService {
    
    private final UploadJobRepository uploadJobRepository;
    private final UploadItemRepository uploadItemRepository;
    private final LambdaService lambdaService;
    private final RateLimitService rateLimitService;
    
    @Value("${image-service.max-urls-per-request:50}")
    private int maxUrlsPerRequest;
    
    /**
     * Tạo upload job mới và gửi cho Lambda xử lý
     */
    @Transactional
    public UploadJob createUploadJob(String userId, List<String> urls) {
        // Validate request
        validateUploadRequest(userId, urls);
        
        // Rate limit is checked in Controller layer
        
        String jobId = UUID.randomUUID().toString();
        
        log.info("Creating upload job {} for user {} with {} URLs", jobId, userId, urls.size());
        
        // Create job entity
        UploadJob job = UploadJob.builder()
                .jobId(jobId)
                .userId(userId)
                .status(UploadJob.JobStatus.PENDING)
                .total(urls.size())
                .completed(0)
                .failed(0)
                .createdAt(LocalDateTime.now())
                .build();
        
        // Save job first
        job = uploadJobRepository.save(job);
        
        // Create items
        for (String url : urls) {
            UploadItem item = UploadItem.builder()
                    .itemId(UUID.randomUUID().toString())
                    .originalUrl(url)
                    .status(UploadItem.ItemStatus.QUEUED)
                    .uploadJob(job)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            job.getItems().add(item);
        }
        
        // Save items
        uploadItemRepository.saveAll(job.getItems());
        
        // Update job status to SENT_TO_LAMBDA
        job.setStatus(UploadJob.JobStatus.SENT_TO_LAMBDA);
        uploadJobRepository.save(job);
        
        // Build minimal item payloads for the worker (no DB access on lambda)
        java.util.List<java.util.Map<String, Object>> itemPayloads = job.getItems().stream()
                .map(it -> java.util.Map.<String, Object>of(
                        "itemId", it.getItemId(),
                        "originalUrl", it.getOriginalUrl()
                ))
                .toList();
        
        // Send to Lambda asynchronously
        sendToLambdaAsync(jobId, itemPayloads);
        
        log.info("Upload job {} created and sent to Lambda", jobId);
        
        return job;
    }
    
    /**
     * Gửi job cho Lambda xử lý (async)
     */
    private void sendToLambdaAsync(String jobId, java.util.List<java.util.Map<String, Object>> items) {
        CompletableFuture<String> lambdaFuture = lambdaService.invokeUploadProcessorAsync(jobId, items);
        
        lambdaFuture.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("Lambda invocation failed for job {}: {}", jobId, throwable.getMessage());
                handleLambdaError(jobId, throwable.getMessage());
            } else {
                log.info("Lambda invocation successful for job {}: {}", jobId, result);
                updateJobLambdaRequestId(jobId, result);
            }
        });
    }
    
    /**
     * Xử lý khi Lambda gặp lỗi
     */
    @Transactional
    public void handleLambdaError(String jobId, String errorMessage) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(UploadJob.JobStatus.FAILED);
            job.setErrorMessage(errorMessage);
            job.setFinishedAt(LocalDateTime.now());
            job.calculateProcessingTime();
            uploadJobRepository.save(job);
        });
    }
    
    /**
     * Update Lambda request ID
     */
    @Transactional
    public void updateJobLambdaRequestId(String jobId, String lambdaRequestId) {
        uploadJobRepository.findById(jobId).ifPresent(job -> {
            job.setLambdaRequestId(lambdaRequestId);
            uploadJobRepository.save(job);
        });
    }
    
    /**
     * Callback từ Lambda để update từng item (Lambda sẽ gọi endpoint này cho mỗi item)
     */
    @Transactional
    public void updateJobItemFromLambda(String jobId, String itemId, UploadItem.ItemStatus status,
                                       String cdnUrl, String s3Key, Long sizeBytes, 
                                       Integer width, Integer height, String errorMessage,
                                       Integer dpiX, Integer dpiY, String format, String colorSpace,
                                       Integer channels, Boolean hasAlpha, Boolean isOpaque,
                                       Integer orientation, String profileName, String compression,
                                       LocalDateTime processedAt) {
        
        log.info("Receiving lambda callback for job {} item {}: status={}", jobId, itemId, status);
        
        // Update item trước
        UploadItem item = uploadItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found: " + itemId));
        
        item.setStatus(status);
        item.setCdnUrl(cdnUrl);
        item.setS3Key(s3Key);
        item.setSizeBytes(sizeBytes);
        item.setWidth(width);
        item.setHeight(height);
        item.setDpiX(dpiX);
        item.setDpiY(dpiY);
        item.setFormat(format);
        item.setColorSpace(colorSpace);
        item.setChannels(channels);
        item.setHasAlpha(hasAlpha);
        item.setIsOpaque(isOpaque);
        item.setOrientation(orientation);
        item.setProfileName(profileName);
        item.setCompression(compression);
        item.setErrorMessage(errorMessage);
        item.setProcessedAt(processedAt);
        item.setUpdatedAt(LocalDateTime.now());
        
        uploadItemRepository.save(item);
        
        // Update job counters atomically
        if (status == UploadItem.ItemStatus.COMPLETED) {
            uploadJobRepository.incrementCompletedCount(jobId);
        } else if (status == UploadItem.ItemStatus.FAILED) {
            uploadJobRepository.incrementFailedCount(jobId);
        }
        
        // Check if job is finished
        UploadJob job = uploadJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
        
        int totalItems = job.getTotal();
        int completedItems = job.getCompleted();
        int failedItems = job.getFailed();
        
        if (completedItems + failedItems >= totalItems) {
            // Job finished - determine final status
            UploadJob.JobStatus finalStatus;
            if (failedItems == 0) {
                finalStatus = UploadJob.JobStatus.COMPLETED;
            } else if (completedItems == 0) {
                finalStatus = UploadJob.JobStatus.FAILED;
            } else {
                finalStatus = UploadJob.JobStatus.PARTIALLY_COMPLETED;
            }
            
            job.setStatus(finalStatus);
            job.setFinishedAt(LocalDateTime.now());
            job.calculateProcessingTime();
            uploadJobRepository.save(job);
            
            log.info("Job {} finished with status: {}", jobId, finalStatus);
        }
        
        log.info("Item {} updated from lambda callback", itemId);
    }
    
    /**
     * Update individual items from Lambda callback
     */
    @Transactional
    public void updateJobItems(String jobId, List<UploadItem> updatedItems) {
        for (UploadItem updatedItem : updatedItems) {
            uploadItemRepository.findById(updatedItem.getItemId()).ifPresent(existingItem -> {
                existingItem.setStatus(updatedItem.getStatus());
                existingItem.setCdnUrl(updatedItem.getCdnUrl());
                existingItem.setS3Key(updatedItem.getS3Key());
                existingItem.setSizeBytes(updatedItem.getSizeBytes());
                existingItem.setWidth(updatedItem.getWidth());
                existingItem.setHeight(updatedItem.getHeight());
                existingItem.setDpiX(updatedItem.getDpiX());
                existingItem.setDpiY(updatedItem.getDpiY());
                existingItem.setFormat(updatedItem.getFormat());
                existingItem.setColorSpace(updatedItem.getColorSpace());
                existingItem.setChannels(updatedItem.getChannels());
                existingItem.setHasAlpha(updatedItem.getHasAlpha());
                existingItem.setIsOpaque(updatedItem.getIsOpaque());
                existingItem.setOrientation(updatedItem.getOrientation());
                existingItem.setProfileName(updatedItem.getProfileName());
                existingItem.setCompression(updatedItem.getCompression());
                existingItem.setErrorMessage(updatedItem.getErrorMessage());
                existingItem.setProcessedAt(updatedItem.getProcessedAt());
                existingItem.setUpdatedAt(LocalDateTime.now());
                
                uploadItemRepository.save(existingItem);
            });
        }
    }
    
    /**
     * Get job by ID and user ID (security check)
     */
    public UploadJob getJobByIdAndUserId(String jobId, String userId) {
        return uploadJobRepository.findByJobIdAndUserId(jobId, userId)
                .orElse(null);
    }
    
    /**
     * Get job by ID with items
     */
    public UploadJob getJobWithItems(String jobId, String userId) {
        return uploadJobRepository.findByJobIdAndUserIdWithItems(jobId, userId)
                .orElse(null);
    }
    
    /**
     * Get CDN URLs for completed job
     */
    public List<String> getCdnUrls(UploadJob job) {
        if (job.getStatus() != UploadJob.JobStatus.COMPLETED && 
            job.getStatus() != UploadJob.JobStatus.PARTIALLY_COMPLETED) {
            return List.of();
        }
        
        return job.getItems().stream()
                .filter(item -> item.getStatus() == UploadItem.ItemStatus.COMPLETED)
                .map(UploadItem::getCdnUrl)
                .filter(url -> url != null && !url.isEmpty())
                .toList();
    }
    
    /**
     * Get user's recent jobs
     */
    public List<UploadJob> getUserRecentJobs(String userId, int limit) {
        return uploadJobRepository.findByUserIdOrderByCreatedAtDesc(userId, 
                org.springframework.data.domain.PageRequest.of(0, limit));
    }
    
    /**
     * Validate upload request
     */
    private void validateUploadRequest(String userId, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException("URLs list cannot be empty");
        }
        
        if (urls.size() > maxUrlsPerRequest) {
            throw new IllegalArgumentException("Too many URLs. Maximum allowed: " + maxUrlsPerRequest);
        }
        
        // Validate each URL
        for (String url : urls) {
            if (url == null || url.trim().isEmpty()) {
                throw new IllegalArgumentException("URL cannot be empty");
            }
            
            if (url.length() > 2000) {
                throw new IllegalArgumentException("URL too long: " + url.substring(0, 100) + "...");
            }
            
            // Basic URL format validation
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new IllegalArgumentException("Invalid URL format: " + url);
            }
        }
    }
    
    /**
     * Health check - count active jobs
     */
    public java.util.Map<String, Object> getHealthStats() {
        long pendingJobs = uploadJobRepository.countByStatus(UploadJob.JobStatus.PENDING);
        long inProgressJobs = uploadJobRepository.countByStatus(UploadJob.JobStatus.IN_PROGRESS);
        long sentToLambdaJobs = uploadJobRepository.countByStatus(UploadJob.JobStatus.SENT_TO_LAMBDA);
        
        return java.util.Map.of(
            "pendingJobs", pendingJobs,
            "inProgressJobs", inProgressJobs,
            "sentToLambdaJobs", sentToLambdaJobs,
            "totalActiveJobs", pendingJobs + inProgressJobs + sentToLambdaJobs,
            "lambdaHealthy", lambdaService.isLambdaHealthy()
        );
    }
}