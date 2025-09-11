package com.example.imageservice.dto;

import com.example.imageservice.entity.BatchMockupJob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchMockupResponseDto {
    
    private String batchJobId;
    private List<BatchMockupItemResponseDto> items;
    private String message;
    private LocalDateTime createdAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchMockupItemResponseDto {
        private String itemId;
        private String backgroundUrl;
        private String designUrl;
        private Integer designX;
        private Integer designY;
        private Double designScale;
        private Double designRotation;
        private String outputFormat;
        private Integer outputQuality;
        private Integer opacity;
        private BatchMockupJob.JobStatus status;
        private String resultUrl;
        private String errorMessage;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        
        public static BatchMockupItemResponseDto fromEntity(BatchMockupJob entity) {
            return BatchMockupItemResponseDto.builder()
                    .itemId(entity.getItemId())
                    .backgroundUrl(entity.getBackgroundUrl())
                    .designUrl(entity.getDesignUrl())
                    .designX(entity.getDesignX())
                    .designY(entity.getDesignY())
                    .designScale(entity.getDesignScale())
                    .designRotation(entity.getDesignRotation())
                    .outputFormat(entity.getOutputFormat())
                    .outputQuality(entity.getOutputQuality())
                    .opacity(entity.getOpacity())
                    .status(entity.getStatus())
                    .resultUrl(entity.getResultUrl())
                    .errorMessage(entity.getErrorMessage())
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    .build();
        }
    }
    
    public static BatchMockupResponseDto fromEntities(String batchJobId, List<BatchMockupJob> entities) {
        List<BatchMockupItemResponseDto> items = entities.stream()
                .map(BatchMockupItemResponseDto::fromEntity)
                .toList();
        
        return BatchMockupResponseDto.builder()
                .batchJobId(batchJobId)
                .items(items)
                .message("Batch mockup jobs retrieved successfully")
                .createdAt(entities.isEmpty() ? null : entities.get(0).getCreatedAt())
                .build();
    }
}