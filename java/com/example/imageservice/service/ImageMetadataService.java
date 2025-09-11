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
     * Lấy thống kê metadata của user
     */
    public ImageMetadataStats getUserMetadataStats(String userId) {
        List<UploadItem> userItems = uploadItemRepository.findByUploadJobUserId(userId);
        
        return ImageMetadataStats.builder()
                .totalImages(userItems.size())
                .totalSizeBytes(userItems.stream()
                        .mapToLong(item -> item.getSizeBytes() != null ? item.getSizeBytes() : 0)
                        .sum())
                .formats(userItems.stream()
                        .map(UploadItem::getFormat)
                        .filter(format -> format != null)
                        .collect(Collectors.groupingBy(format -> format, Collectors.counting())))
                .averageWidth(userItems.stream()
                        .mapToInt(item -> item.getWidth() != null ? item.getWidth() : 0)
                        .average()
                        .orElse(0.0))
                .averageHeight(userItems.stream()
                        .mapToInt(item -> item.getHeight() != null ? item.getHeight() : 0)
                        .average()
                        .orElse(0.0))
                .build();
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
    
    /**
     * DTO cho thống kê metadata
     */
    public static class ImageMetadataStats {
        private int totalImages;
        private long totalSizeBytes;
        private java.util.Map<String, Long> formats;
        private double averageWidth;
        private double averageHeight;
        
        // Getters and setters
        public int getTotalImages() { return totalImages; }
        public void setTotalImages(int totalImages) { this.totalImages = totalImages; }
        
        public long getTotalSizeBytes() { return totalSizeBytes; }
        public void setTotalSizeBytes(long totalSizeBytes) { this.totalSizeBytes = totalSizeBytes; }
        
        public java.util.Map<String, Long> getFormats() { return formats; }
        public void setFormats(java.util.Map<String, Long> formats) { this.formats = formats; }
        
        public double getAverageWidth() { return averageWidth; }
        public void setAverageWidth(double averageWidth) { this.averageWidth = averageWidth; }
        
        public double getAverageHeight() { return averageHeight; }
        public void setAverageHeight(double averageHeight) { this.averageHeight = averageHeight; }
        
        public static ImageMetadataStatsBuilder builder() {
            return new ImageMetadataStatsBuilder();
        }
        
        public static class ImageMetadataStatsBuilder {
            private ImageMetadataStats stats = new ImageMetadataStats();
            
            public ImageMetadataStatsBuilder totalImages(int totalImages) {
                stats.totalImages = totalImages;
                return this;
            }
            
            public ImageMetadataStatsBuilder totalSizeBytes(long totalSizeBytes) {
                stats.totalSizeBytes = totalSizeBytes;
                return this;
            }
            
            public ImageMetadataStatsBuilder formats(java.util.Map<String, Long> formats) {
                stats.formats = formats;
                return this;
            }
            
            public ImageMetadataStatsBuilder averageWidth(double averageWidth) {
                stats.averageWidth = averageWidth;
                return this;
            }
            
            public ImageMetadataStatsBuilder averageHeight(double averageHeight) {
                stats.averageHeight = averageHeight;
                return this;
            }
            
            public ImageMetadataStats build() {
                return stats;
            }
        }
    }
}
