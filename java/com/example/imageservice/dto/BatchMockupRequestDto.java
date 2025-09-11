package com.example.imageservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchMockupRequestDto {

    @NotBlank(message = "backgroundUrl is required")
    private String backgroundUrl;

    @NotBlank(message = "designUrl is required")
    private String designUrl;

    // Cấu trúc "phẳng" ban đầu
    private Integer designX;
    private Integer designY;
    private Double designScale;
    private Double designRotation;
    private String outputFormat;
    private Integer outputQuality;
    private Integer opacity;
    private MockupRequestDto.RemoveBackgroundOptions removeBackground;
    private MockupRequestDto.WarpOptions warp;
    private MockupRequestDto.DisplaceOptions displace;
}