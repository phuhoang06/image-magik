package com.example.imageservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Design Quality Validation Service
 * Validates extracted designs for quality and usability
 */
@Service
@Slf4j
public class DesignQualityValidationService {

    /**
     * Validate design quality and return quality metrics
     */
    public DesignQualityReport validateDesignQuality(BufferedImage designImage, 
                                                   MultiDesignDetectionService.EnhancedDesignRegion region) {
        log.debug("Validating design quality for region: {}x{} at ({}, {})", 
            region.getWidth(), region.getHeight(), region.getX(), region.getY());
        
        DesignQualityReport report = new DesignQualityReport();
        
        // 1. Dimension validation
        report.setDimensionScore(validateDimensions(designImage, region));
        
        // 2. Content validation
        report.setContentScore(validateContent(designImage));
        
        // 3. Transparency validation
        report.setTransparencyScore(validateTransparency(designImage));
        
        // 4. Edge quality validation
        report.setEdgeScore(validateEdgeQuality(designImage));
        
        // 5. Color distribution validation
        report.setColorScore(validateColorDistribution(designImage));
        
        // 6. Overall quality score
        double overallScore = calculateOverallScore(report);
        report.setOverallScore(overallScore);
        
        // 7. Quality assessment
        report.setQualityLevel(determineQualityLevel(overallScore));
        
        // 8. Recommendations
        report.setRecommendations(generateRecommendations(report));
        
        log.debug("Design quality validation completed. Overall score: {}", overallScore);
        return report;
    }

    /**
     * Validate design dimensions
     */
    private double validateDimensions(BufferedImage designImage, MultiDesignDetectionService.EnhancedDesignRegion region) {
        int width = designImage.getWidth();
        int height = designImage.getHeight();
        
        // Check minimum dimensions
        if (width < 20 || height < 20) {
            return 0.0; // Too small
        }
        
        // Check aspect ratio (not too extreme)
        double aspectRatio = (double) width / height;
        if (aspectRatio > 10 || aspectRatio < 0.1) {
            return 0.3; // Poor aspect ratio
        }
        
        // Check if dimensions match region
        if (width != region.getWidth() || height != region.getHeight()) {
            return 0.7; // Dimension mismatch
        }
        
        // Good dimensions
        return 1.0;
    }

    /**
     * Validate design content (not empty, has meaningful content)
     */
    private double validateContent(BufferedImage designImage) {
        int width = designImage.getWidth();
        int height = designImage.getHeight();
        
        // Count non-transparent pixels
        int nonTransparentPixels = 0;
        int totalPixels = width * height;
        
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rgb = designImage.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;
                if (alpha > 10) { // Not fully transparent
                    nonTransparentPixels++;
                }
            }
        }
        
        double contentRatio = (double) nonTransparentPixels / totalPixels;
        
        if (contentRatio < 0.01) {
            return 0.0; // Too little content
        } else if (contentRatio < 0.1) {
            return 0.3; // Low content
        } else if (contentRatio < 0.3) {
            return 0.7; // Moderate content
        } else {
            return 1.0; // Good content
        }
    }

    /**
     * Validate transparency (should have some transparency for design extraction)
     */
    private double validateTransparency(BufferedImage designImage) {
        int width = designImage.getWidth();
        int height = designImage.getHeight();
        
        int transparentPixels = 0;
        int semiTransparentPixels = 0;
        int totalPixels = width * height;
        
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rgb = designImage.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;
                
                if (alpha < 10) {
                    transparentPixels++;
                } else if (alpha < 255) {
                    semiTransparentPixels++;
                }
            }
        }
        
        double transparencyRatio = (double) transparentPixels / totalPixels;
        double semiTransparencyRatio = (double) semiTransparentPixels / totalPixels;
        
        // Good if has some transparency (background removed)
        if (transparencyRatio > 0.1 && transparencyRatio < 0.8) {
            return 1.0;
        } else if (semiTransparencyRatio > 0.1) {
            return 0.8;
        } else if (transparencyRatio > 0.8) {
            return 0.5; // Too much transparency
        } else {
            return 0.3; // No transparency (background not removed)
        }
    }

    /**
     * Validate edge quality (sharp edges, not blurry)
     */
    private double validateEdgeQuality(BufferedImage designImage) {
        int width = designImage.getWidth();
        int height = designImage.getHeight();
        
        if (width < 3 || height < 3) {
            return 0.5; // Too small for edge detection
        }
        
        int edgePixels = 0;
        int totalPixels = (width - 2) * (height - 2);
        
        // Simple edge detection using gradient
        for (int x = 1; x < width - 1; x++) {
            for (int y = 1; y < height - 1; y++) {
                int rgb = designImage.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;
                
                if (alpha < 10) continue; // Skip transparent pixels
                
                // Calculate gradient
                int rgbX1 = designImage.getRGB(x - 1, y);
                int rgbX2 = designImage.getRGB(x + 1, y);
                int rgbY1 = designImage.getRGB(x, y - 1);
                int rgbY2 = designImage.getRGB(x, y + 1);
                
                int gradientX = Math.abs(getGrayValue(rgbX2) - getGrayValue(rgbX1));
                int gradientY = Math.abs(getGrayValue(rgbY2) - getGrayValue(rgbY1));
                int gradient = Math.max(gradientX, gradientY);
                
                if (gradient > 30) { // Threshold for edge
                    edgePixels++;
                }
            }
        }
        
        double edgeRatio = (double) edgePixels / totalPixels;
        
        if (edgeRatio > 0.1) {
            return 1.0; // Good edge definition
        } else if (edgeRatio > 0.05) {
            return 0.7; // Moderate edge definition
        } else {
            return 0.4; // Poor edge definition (blurry)
        }
    }

    /**
     * Get grayscale value from RGB
     */
    private int getGrayValue(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (r + g + b) / 3;
    }

    /**
     * Validate color distribution (not monochrome, good color variety)
     */
    private double validateColorDistribution(BufferedImage designImage) {
        int width = designImage.getWidth();
        int height = designImage.getHeight();
        
        Map<Integer, Integer> colorCount = new HashMap<>();
        int totalPixels = 0;
        
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rgb = designImage.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;
                
                if (alpha < 10) continue; // Skip transparent pixels
                
                // Quantize colors (reduce precision for counting)
                int quantizedRgb = quantizeColor(rgb);
                colorCount.put(quantizedRgb, colorCount.getOrDefault(quantizedRgb, 0) + 1);
                totalPixels++;
            }
        }
        
        if (totalPixels == 0) {
            return 0.0;
        }
        
        int uniqueColors = colorCount.size();
        double colorVariety = (double) uniqueColors / Math.min(totalPixels, 1000); // Normalize
        
        if (colorVariety > 0.1) {
            return 1.0; // Good color variety
        } else if (colorVariety > 0.05) {
            return 0.7; // Moderate color variety
        } else {
            return 0.4; // Poor color variety (monochrome)
        }
    }

    /**
     * Quantize color to reduce precision
     */
    private int quantizeColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        
        // Quantize to 4-bit per channel
        r = (r >> 4) << 4;
        g = (g >> 4) << 4;
        b = (b >> 4) << 4;
        
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Calculate overall quality score
     */
    private double calculateOverallScore(DesignQualityReport report) {
        double dimensionWeight = 0.2;
        double contentWeight = 0.3;
        double transparencyWeight = 0.2;
        double edgeWeight = 0.15;
        double colorWeight = 0.15;
        
        return report.getDimensionScore() * dimensionWeight +
               report.getContentScore() * contentWeight +
               report.getTransparencyScore() * transparencyWeight +
               report.getEdgeScore() * edgeWeight +
               report.getColorScore() * colorWeight;
    }

    /**
     * Determine quality level based on score
     */
    private String determineQualityLevel(double score) {
        if (score >= 0.8) {
            return "EXCELLENT";
        } else if (score >= 0.6) {
            return "GOOD";
        } else if (score >= 0.4) {
            return "ACCEPTABLE";
        } else {
            return "POOR";
        }
    }

    /**
     * Generate recommendations for improvement
     */
    private Map<String, String> generateRecommendations(DesignQualityReport report) {
        Map<String, String> recommendations = new HashMap<>();
        
        if (report.getDimensionScore() < 0.7) {
            recommendations.put("dimensions", "Consider resizing the design to improve aspect ratio");
        }
        
        if (report.getContentScore() < 0.7) {
            recommendations.put("content", "Design may be too sparse, consider adding more visual elements");
        }
        
        if (report.getTransparencyScore() < 0.7) {
            recommendations.put("transparency", "Background removal may be needed for better design extraction");
        }
        
        if (report.getEdgeScore() < 0.7) {
            recommendations.put("edges", "Design may be blurry, consider using higher resolution source");
        }
        
        if (report.getColorScore() < 0.7) {
            recommendations.put("colors", "Consider adding more color variety to the design");
        }
        
        return recommendations;
    }

    /**
     * Design Quality Report
     */
    public static class DesignQualityReport {
        private double dimensionScore;
        private double contentScore;
        private double transparencyScore;
        private double edgeScore;
        private double colorScore;
        private double overallScore;
        private String qualityLevel;
        private Map<String, String> recommendations;

        public DesignQualityReport() {
            this.recommendations = new HashMap<>();
        }

        // Getters and Setters
        public double getDimensionScore() { return dimensionScore; }
        public void setDimensionScore(double dimensionScore) { this.dimensionScore = dimensionScore; }
        
        public double getContentScore() { return contentScore; }
        public void setContentScore(double contentScore) { this.contentScore = contentScore; }
        
        public double getTransparencyScore() { return transparencyScore; }
        public void setTransparencyScore(double transparencyScore) { this.transparencyScore = transparencyScore; }
        
        public double getEdgeScore() { return edgeScore; }
        public void setEdgeScore(double edgeScore) { this.edgeScore = edgeScore; }
        
        public double getColorScore() { return colorScore; }
        public void setColorScore(double colorScore) { this.colorScore = colorScore; }
        
        public double getOverallScore() { return overallScore; }
        public void setOverallScore(double overallScore) { this.overallScore = overallScore; }
        
        public String getQualityLevel() { return qualityLevel; }
        public void setQualityLevel(String qualityLevel) { this.qualityLevel = qualityLevel; }
        
        public Map<String, String> getRecommendations() { return recommendations; }
        public void setRecommendations(Map<String, String> recommendations) { this.recommendations = recommendations; }
    }
}
