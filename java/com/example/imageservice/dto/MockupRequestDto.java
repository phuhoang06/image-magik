package com.example.imageservice.dto;

import lombok.Data;

@Data
public class MockupRequestDto {
    private String backgroundUrl;
    private String designUrl;
    private Integer designX;
    private Integer designY;
    private Double designScale;
    private Double designRotation;
    private String outputFormat;
    private Integer outputQuality;
    private Integer opacity;
    private RemoveBackgroundOptions removeBackground;
    private WarpOptions warp;
    private DisplaceOptions displace;

    @Data
    public static class RemoveBackgroundOptions {
        private Boolean enabled;
        private String method;
        private String color;
        private String fuzz;
    }

    @Data
    public static class WarpOptions {
        private String type;
        private String points;
    }

    @Data
    public static class DisplaceOptions {
        private Boolean enabled;
        private Integer amountX;
        private Integer amountY;
    }
}
