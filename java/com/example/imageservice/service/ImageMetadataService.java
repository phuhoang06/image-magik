package com.example.imageservice.service;

import com.example.imageservice.dto.ImageMetadataDto;
import com.example.imageservice.dto.EnrichedMetadataDto;
import com.example.imageservice.entity.UploadItem;
import com.example.imageservice.repository.UploadItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageMetadataService {
    
    private final UploadItemRepository uploadItemRepository;
    private final ImageMagickMetadataService imageMagickMetadataService;
    
    /**
     * Lấy metadata chi tiết của một ảnh
     */
    public Optional<ImageMetadataDto> getImageMetadata(String itemId, String userId) {
        return uploadItemRepository.findByItemIdAndUploadJobUserId(itemId, userId)
                .map(this::convertToMetadataDto);
    }
    
    /**
     * Lấy enriched metadata (bao gồm EXIF, GPS, Camera) bằng ImageMagick
     */
    public Optional<EnrichedMetadataDto> getEnrichedMetadata(String itemId, String userId) {
        // Kiểm tra quyền truy cập
        if (!uploadItemRepository.findByItemIdAndUploadJobUserId(itemId, userId).isPresent()) {
            return Optional.empty();
        }
        
        return imageMagickMetadataService.enrichMetadata(itemId)
                .map(this::convertToEnrichedDto);
    }
    
    /**
     * Lấy danh sách metadata của tất cả ảnh trong một job
     */
    public List<ImageMetadataDto> getJobImagesMetadata(String jobId, String userId) {
        return uploadItemRepository.findByUploadJobJobIdAndUploadJobUserId(jobId, userId)
                .stream()
                .map(this::convertToMetadataDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Tìm kiếm ảnh theo metadata
     */
    public Page<ImageMetadataDto> searchImagesByMetadata(String userId, 
                                                        String format, 
                                                        Integer minWidth, 
                                                        Integer maxWidth,
                                                        Integer minHeight, 
                                                        Integer maxHeight,
                                                        Long minSize, 
                                                        Long maxSize,
                                                        Boolean hasAlpha,
                                                        Pageable pageable) {
        
        Page<UploadItem> items = uploadItemRepository.findByMetadataCriteria(
                userId, format, minWidth, maxWidth, minHeight, maxHeight, 
                minSize, maxSize, hasAlpha, pageable);
        
        return items.map(this::convertToMetadataDto);
    }
    
    
    /**
     * Chuyển đổi UploadItem thành ImageMetadataDto
     */
    private ImageMetadataDto convertToMetadataDto(UploadItem item) {
        return ImageMetadataDto.builder()
                .itemId(item.getItemId())
                .jobId(item.getJobId())
                .originalUrl(item.getOriginalUrl())
                .cdnUrl(item.getCdnUrl())
                .s3Key(item.getS3Key())
                .sizeBytes(item.getSizeBytes())
                .width(item.getWidth())
                .height(item.getHeight())
                .dpiX(item.getDpiX())
                .dpiY(item.getDpiY())
                .format(item.getFormat())
                .colorSpace(item.getColorSpace())
                .channels(item.getChannels())
                .hasAlpha(item.getHasAlpha())
                .isOpaque(item.getIsOpaque())
                .orientation(item.getOrientation())
                .profileName(item.getProfileName())
                .compression(item.getCompression())
                .status(item.getStatus().name())
                .errorMessage(item.getErrorMessage())
                .createdAt(item.getCreatedAt())
                .processedAt(item.getProcessedAt())
                .build();
    }
    
    /**
     * Chuyển đổi ImageMagick EnrichedMetadata thành EnrichedMetadataDto
     */
    private EnrichedMetadataDto convertToEnrichedDto(ImageMagickMetadataService.EnrichedMetadata metadata) {
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
