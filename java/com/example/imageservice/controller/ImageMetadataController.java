package com.example.imageservice.controller;

import com.example.imageservice.dto.ImageMetadataDto;
import com.example.imageservice.dto.EnrichedMetadataDto;
import com.example.imageservice.service.ImageMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/metadata")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ImageMetadataController {

    private final ImageMetadataService imageMetadataService;

    /**
     * Lấy metadata chi tiết của một ảnh
     * GET /api/v1/metadata/images/{itemId}
     */
    @GetMapping("/images/{itemId}")
    public ResponseEntity<?> getImageMetadata(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String itemId) {

        try {
            String userId = getUserId(jwt);
            log.debug("Metadata request for image {} from user {}", itemId, userId);

            Optional<ImageMetadataDto> metadata = imageMetadataService.getImageMetadata(itemId, userId);

            if (metadata.isPresent()) {
                return ResponseEntity.ok(metadata.get());
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("Failed to get image metadata: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "Failed to get image metadata"));
        }
    }
    
    /**
     * Lấy enriched metadata (EXIF, GPS, Camera) bằng ImageMagick
     * GET /api/v1/metadata/images/{itemId}/enriched
     */
    @GetMapping("/images/{itemId}/enriched")
    public ResponseEntity<?> getEnrichedMetadata(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String itemId) {

        try {
            String userId = getUserId(jwt);
            log.debug("Enriched metadata request for image {} from user {}", itemId, userId);

            Optional<EnrichedMetadataDto> enrichedMetadata = imageMetadataService.getEnrichedMetadata(itemId, userId);

            if (enrichedMetadata.isPresent()) {
                return ResponseEntity.ok(enrichedMetadata.get());
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("Failed to get enriched metadata: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "Failed to get enriched metadata"));
        }
    }

    /**
     * Lấy metadata của tất cả ảnh trong một job
     * GET /api/v1/metadata/jobs/{jobId}/images
     */
    @GetMapping("/jobs/{jobId}/images")
    public ResponseEntity<?> getJobImagesMetadata(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String jobId) {

        try {
            String userId = getUserId(jwt);
            log.debug("Job metadata request for job {} from user {}", jobId, userId);

            var metadataList = imageMetadataService.getJobImagesMetadata(jobId, userId);

            return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "totalImages", metadataList.size(),
                "images", metadataList
            ));

        } catch (Exception e) {
            log.error("Failed to get job images metadata: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "Failed to get job images metadata"));
        }
    }

    /**
     * Tìm kiếm ảnh theo metadata
     * GET /api/v1/metadata/search
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchImagesByMetadata(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) Integer minWidth,
            @RequestParam(required = false) Integer maxWidth,
            @RequestParam(required = false) Integer minHeight,
            @RequestParam(required = false) Integer maxHeight,
            @RequestParam(required = false) Long minSize,
            @RequestParam(required = false) Long maxSize,
            @RequestParam(required = false) Boolean hasAlpha,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            String userId = getUserId(jwt);
            log.debug("Search request from user {} with criteria: format={}, minWidth={}, maxWidth={}, minHeight={}, maxHeight={}, minSize={}, maxSize={}, hasAlpha={}",
                    userId, format, minWidth, maxWidth, minHeight, maxHeight, minSize, maxSize, hasAlpha);

            Pageable pageable = PageRequest.of(page, size);
            Page<ImageMetadataDto> results = imageMetadataService.searchImagesByMetadata(
                    userId, format, minWidth, maxWidth, minHeight, maxHeight, minSize, maxSize, hasAlpha, pageable);

            return ResponseEntity.ok(Map.of(
                "content", results.getContent(),
                "totalElements", results.getTotalElements(),
                "totalPages", results.getTotalPages(),
                "currentPage", results.getNumber(),
                "pageSize", results.getSize(),
                "hasNext", results.hasNext(),
                "hasPrevious", results.hasPrevious()
            ));

        } catch (Exception e) {
            log.error("Failed to search images by metadata: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "Failed to search images by metadata"));
        }
    }

    /**
     * Lấy thống kê metadata của user
     * GET /api/v1/metadata/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getUserMetadataStats(@AuthenticationPrincipal Jwt jwt) {

        try {
            String userId = getUserId(jwt);
            log.debug("Stats request from user {}", userId);

            var stats = imageMetadataService.getUserMetadataStats(userId);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Failed to get user metadata stats: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "Failed to get user metadata stats"));
        }
    }

    /**
     * Lấy ảnh theo format
     * GET /api/v1/metadata/formats/{format}
     */
    @GetMapping("/formats/{format}")
    public ResponseEntity<?> getImagesByFormat(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String format) {

        try {
            String userId = getUserId(jwt);
            log.debug("Format search request from user {} for format {}", userId, format);

            var images = imageMetadataService.getJobImagesMetadata(null, userId)
                    .stream()
                    .filter(img -> format.equalsIgnoreCase(img.getFormat()))
                    .toList();

            return ResponseEntity.ok(Map.of(
                "format", format,
                "totalImages", images.size(),
                "images", images
            ));

        } catch (Exception e) {
            log.error("Failed to get images by format: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "Failed to get images by format"));
        }
    }

    /**
     * Lấy ảnh có alpha channel
     * GET /api/v1/metadata/alpha
     */
    @GetMapping("/alpha")
    public ResponseEntity<?> getImagesWithAlpha(@AuthenticationPrincipal Jwt jwt) {

        try {
            String userId = getUserId(jwt);
            log.debug("Alpha channel search request from user {}", userId);

            var images = imageMetadataService.getJobImagesMetadata(null, userId)
                    .stream()
                    .filter(ImageMetadataDto::getHasAlpha)
                    .toList();

            return ResponseEntity.ok(Map.of(
                "totalImages", images.size(),
                "images", images
            ));

        } catch (Exception e) {
            log.error("Failed to get images with alpha: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "INTERNAL_ERROR", "message", "Failed to get images with alpha"));
        }
    }

    /**
     * Extract user ID from JWT
     */
    private String getUserId(Jwt jwt) {
        return jwt.getSubject();
    }
}
