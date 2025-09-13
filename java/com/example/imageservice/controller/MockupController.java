package com.example.imageservice.controller;

import com.example.imageservice.dto.BatchMockupRequestDto;
import com.example.imageservice.dto.BatchMockupResponseDto;
import com.example.imageservice.dto.MockupRequestDto;
import com.example.imageservice.dto.MockupResponseDto;
import com.example.imageservice.entity.BatchMockupJob;
import com.example.imageservice.entity.MockupJob;
import com.example.imageservice.service.BatchMockupService;
import com.example.imageservice.service.MockupService;
import com.example.imageservice.service.RateLimitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mockup")
@RequiredArgsConstructor
@Validated
public class MockupController {

    private final MockupService mockupService;
    private final BatchMockupService batchMockupService;
    private final RateLimitService rateLimitService;

    @PostMapping
    public ResponseEntity<MockupResponseDto> createMockupJob(
            @Valid @RequestBody MockupRequestDto request,
            Principal principal) {

        String userId = (principal != null) ? principal.getName() : "anonymous";
        rateLimitService.checkUploadRateLimit(userId);

        MockupJob job = mockupService.createMockupJob(request, userId);

        MockupResponseDto response = MockupResponseDto.builder()
                .jobId(job.getJobId())
                .status(job.getStatus())
                .message("Job accepted and sent to Lambda")
                .resultUrl(job.getResultUrl())
                .build();

        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchMockupResponseDto> createBatchMockupJob(
            @Valid @RequestBody List<@Valid BatchMockupRequestDto> requests,
            Principal principal) {

        String userId = (principal != null) ? principal.getName() : "anonymous";
        rateLimitService.checkUploadRateLimit(userId);

        String batchJobId = batchMockupService.createBatchMockupJobs(requests, userId);

        List<BatchMockupJob> entities = batchMockupService.getBatchMockupJobs(batchJobId);
        BatchMockupResponseDto response = BatchMockupResponseDto.fromEntities(batchJobId, entities);
        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<MockupResponseDto> getMockupJob(@PathVariable String jobId) {
        try {
            UUID uuid = UUID.fromString(jobId);
            MockupJob job = mockupService.getMockupJob(uuid);
            
            MockupResponseDto response = MockupResponseDto.builder()
                    .jobId(job.getJobId())
                    .status(job.getStatus())
                    .resultUrl(job.getResultUrl())
                    .message("Job retrieved successfully")
                    .build();
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(MockupResponseDto.builder()
                    .message("Invalid job ID format")
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/batch/{batchJobId}")
    public ResponseEntity<BatchMockupResponseDto> getBatchMockupJob(@PathVariable String batchJobId) {
        try {
            List<BatchMockupJob> entities = batchMockupService.getBatchMockupJobs(batchJobId);
            if (entities.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            BatchMockupResponseDto response = BatchMockupResponseDto.fromEntities(batchJobId, entities);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/batch/{batchJobId}/{itemId}")
    public ResponseEntity<Map<String, Object>> getBatchMockupItem(
            @PathVariable String batchJobId, 
            @PathVariable String itemId) {
        try {
            BatchMockupJob item = batchMockupService.getBatchMockupJob(batchJobId, itemId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("batchJobId", batchJobId);
            response.put("itemId", itemId);
            response.put("status", item.getStatus().name());
            response.put("resultUrl", item.getResultUrl());
            response.put("errorMessage", item.getErrorMessage());
            response.put("backgroundUrl", item.getBackgroundUrl());
            response.put("designUrl", item.getDesignUrl());
            response.put("designX", item.getDesignX());
            response.put("designY", item.getDesignY());
            response.put("designScale", item.getDesignScale());
            response.put("createdAt", item.getCreatedAt());
            response.put("updatedAt", item.getUpdatedAt());
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/admin/cache")
    public ResponseEntity<Map<String, Object>> getCacheInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("sentBatchJobsCount", batchMockupService.getSentBatchJobsCount());
        response.put("message", "Cache info retrieved successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin/cache/clear")
    public ResponseEntity<Map<String, Object>> clearCache() {
        batchMockupService.clearSentBatchJobsCache();
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Cache cleared successfully");
        response.put("sentBatchJobsCount", batchMockupService.getSentBatchJobsCount());
        return ResponseEntity.ok(response);
    }
}
