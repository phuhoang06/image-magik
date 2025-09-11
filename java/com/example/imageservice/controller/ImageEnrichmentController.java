package com.example.imageservice.controller;

import com.example.imageservice.dto.EnrichedMetadataDto;
import com.example.imageservice.service.ImageMagickMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
@Slf4j
public class ImageEnrichmentController {
    
    private final ImageMagickMetadataService imageMagickMetadataService;
    
    /**
     * Enrich metadata cho một ảnh bằng ImageMagick
     */
    @PostMapping("/{itemId}/enrich")
    public ResponseEntity<EnrichedMetadataDto> enrichImageMetadata(
            @PathVariable String itemId,
            @RequestHeader("X-User-ID") String userId) {
        
        log.info("Enriching metadata for item: {} by user: {}", itemId, userId);
        
        try {
            Optional<ImageMagickMetadataService.EnrichedMetadata> enrichedMetadata = 
                    imageMagickMetadataService.enrichMetadata(itemId);
            
            if (enrichedMetadata.isPresent()) {
                EnrichedMetadataDto dto = convertToDto(enrichedMetadata.get());
                return ResponseEntity.ok(dto);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error enriching metadata for item {}: {}", itemId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Enrich metadata từ URL ảnh trực tiếp
     */
    @PostMapping("/enrich-url")
    public ResponseEntity<EnrichedMetadataDto> enrichMetadataFromUrl(
            @RequestParam String imageUrl,
            @RequestHeader("X-User-ID") String userId) {
        
        log.info("Enriching metadata from URL: {} by user: {}", imageUrl, userId);
        
        try {
            Optional<ImageMagickMetadataService.EnrichedMetadata> enrichedMetadata = 
                    imageMagickMetadataService.enrichMetadataFromUrl(imageUrl);
            
            if (enrichedMetadata.isPresent()) {
                EnrichedMetadataDto dto = convertToDto(enrichedMetadata.get());
                return ResponseEntity.ok(dto);
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error enriching metadata from URL {}: {}", imageUrl, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Chuyển đổi EnrichedMetadata thành DTO
     */
    private EnrichedMetadataDto convertToDto(ImageMagickMetadataService.EnrichedMetadata metadata) {
        return EnrichedMetadataDto.builder()
                .gpsLatitude(metadata.getGpsLatitude())
                .gpsLongitude(metadata.getGpsLongitude())
                .cameraMake(metadata.getCameraMake())
                .cameraModel(metadata.getCameraModel())
                .focalLength(metadata.getFocalLength())
                .dateTaken(metadata.getDateTaken())
                .orientation(metadata.getOrientation())
                .iccProfile(metadata.getIccProfile())
                .colorSpace(metadata.getColorSpace())
                .compression(metadata.getCompression())
                .quality(metadata.getQuality())
                .bitDepth(metadata.getBitDepth())
                .format(metadata.getFormat())
                .build();
    }
}
