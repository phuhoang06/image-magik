package com.example.imageservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Multi-Design Detection Service
 * Enhanced service for detecting multiple design regions with improved accuracy
 */
@Service
@Slf4j
public class MultiDesignDetectionService {

    @Autowired
    private DesignDetectionService designDetectionService;
    
    @Autowired
    private GenericDesignDetectionService genericDetectionService;
    
    @Autowired
    private CLIPDesignDetectionService clipService;

    /**
     * Detect multiple design regions with enhanced accuracy
     * Combines YOLOv8, Generic Detection, and CLIP classification
     */
    public List<EnhancedDesignRegion> detectMultipleDesigns(MultipartFile imageFile) throws IOException {
        log.info("Starting multi-design detection for image: {}", imageFile.getOriginalFilename());
        
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageFile.getBytes()));
        
        // Step 1: YOLOv8 Detection
        List<DesignDetectionService.DesignRegion> yolov8Regions = designDetectionService.detectDesigns(imageFile);
        log.info("YOLOv8 detected {} regions", yolov8Regions.size());
        
        // Step 2: Generic Detection
        List<GenericDesignDetectionService.DesignRegion> genericRegions = genericDetectionService.detectDesigns(image);
        log.info("Generic detection found {} regions", genericRegions.size());
        
        // Step 3: Merge and deduplicate regions
        List<EnhancedDesignRegion> mergedRegions = mergeDetectionResults(yolov8Regions, genericRegions, image);
        log.info("Merged detection results: {} unique regions", mergedRegions.size());
        
        // Step 4: CLIP Classification for each region
        List<CLIPDesignDetectionService.DesignRegion> clipRegions = convertToCLIPRegions(mergedRegions);
        List<CLIPDesignDetectionService.DesignClassification> classifications = 
            clipService.classifyDesignRegions(image, clipRegions);
        
        // Step 5: Enhance regions with CLIP classifications
        List<EnhancedDesignRegion> enhancedRegions = enhanceWithClassifications(mergedRegions, classifications);
        
        // Step 6: Quality validation and filtering
        List<EnhancedDesignRegion> validatedRegions = validateAndFilterRegions(enhancedRegions, image);
        
        log.info("Final multi-design detection result: {} high-quality regions", validatedRegions.size());
        return validatedRegions;
    }

    /**
     * Merge YOLOv8 and Generic detection results
     */
    private List<EnhancedDesignRegion> mergeDetectionResults(
            List<DesignDetectionService.DesignRegion> yolov8Regions,
            List<GenericDesignDetectionService.DesignRegion> genericRegions,
            BufferedImage image) {
        
        List<EnhancedDesignRegion> mergedRegions = new ArrayList<>();
        
        // Add YOLOv8 regions
        for (DesignDetectionService.DesignRegion region : yolov8Regions) {
            mergedRegions.add(new EnhancedDesignRegion(
                region.getX(), region.getY(), region.getWidth(), region.getHeight(),
                region.getConfidence(), region.getClassName(), "yolov8", image
            ));
        }
        
        // Add Generic regions (avoid duplicates)
        for (GenericDesignDetectionService.DesignRegion region : genericRegions) {
            if (!isDuplicateRegion(region, mergedRegions)) {
                mergedRegions.add(new EnhancedDesignRegion(
                    region.getX(), region.getY(), region.getWidth(), region.getHeight(),
                    region.getConfidence(), region.getLabel(), "generic", image
                ));
            }
        }
        
        // Apply Non-Maximum Suppression to remove overlapping regions
        return applyNonMaximumSuppression(mergedRegions);
    }

    /**
     * Check if a region is duplicate of existing regions
     */
    private boolean isDuplicateRegion(GenericDesignDetectionService.DesignRegion newRegion, 
                                    List<EnhancedDesignRegion> existingRegions) {
        for (EnhancedDesignRegion existing : existingRegions) {
            if (calculateIoU(newRegion, existing) > 0.5) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate Intersection over Union (IoU) between two regions
     */
    private double calculateIoU(GenericDesignDetectionService.DesignRegion region1, EnhancedDesignRegion region2) {
        int x1 = Math.max(region1.getX(), region2.getX());
        int y1 = Math.max(region1.getY(), region2.getY());
        int x2 = Math.min(region1.getX() + region1.getWidth(), region2.getX() + region2.getWidth());
        int y2 = Math.min(region1.getY() + region1.getHeight(), region2.getY() + region2.getHeight());
        
        if (x2 <= x1 || y2 <= y1) {
            return 0.0;
        }
        
        int intersection = (x2 - x1) * (y2 - y1);
        int area1 = region1.getWidth() * region1.getHeight();
        int area2 = region2.getWidth() * region2.getHeight();
        int union = area1 + area2 - intersection;
        
        return (double) intersection / union;
    }

    /**
     * Apply Non-Maximum Suppression to remove overlapping regions
     */
    private List<EnhancedDesignRegion> applyNonMaximumSuppression(List<EnhancedDesignRegion> regions) {
        // Sort by confidence (descending)
        regions.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        
        List<EnhancedDesignRegion> filteredRegions = new ArrayList<>();
        boolean[] suppressed = new boolean[regions.size()];
        
        for (int i = 0; i < regions.size(); i++) {
            if (suppressed[i]) continue;
            
            EnhancedDesignRegion current = regions.get(i);
            filteredRegions.add(current);
            
            // Suppress overlapping regions
            for (int j = i + 1; j < regions.size(); j++) {
                if (suppressed[j]) continue;
                
                EnhancedDesignRegion other = regions.get(j);
                if (calculateIoU(current, other) > 0.3) { // NMS threshold
                    suppressed[j] = true;
                }
            }
        }
        
        return filteredRegions;
    }

    /**
     * Calculate IoU between two EnhancedDesignRegion objects
     */
    private double calculateIoU(EnhancedDesignRegion region1, EnhancedDesignRegion region2) {
        int x1 = Math.max(region1.getX(), region2.getX());
        int y1 = Math.max(region1.getY(), region2.getY());
        int x2 = Math.min(region1.getX() + region1.getWidth(), region2.getX() + region2.getWidth());
        int y2 = Math.min(region1.getY() + region1.getHeight(), region2.getY() + region2.getHeight());
        
        if (x2 <= x1 || y2 <= y1) {
            return 0.0;
        }
        
        int intersection = (x2 - x1) * (y2 - y1);
        int area1 = region1.getWidth() * region1.getHeight();
        int area2 = region2.getWidth() * region2.getHeight();
        int union = area1 + area2 - intersection;
        
        return (double) intersection / union;
    }

    /**
     * Convert EnhancedDesignRegion to CLIP DesignRegion
     */
    private List<CLIPDesignDetectionService.DesignRegion> convertToCLIPRegions(List<EnhancedDesignRegion> regions) {
        return regions.stream()
            .map(region -> new CLIPDesignDetectionService.DesignRegion(
                region.getX(), region.getY(), region.getWidth(), region.getHeight(),
                region.getClassName(), region.getConfidence()
            ))
            .collect(Collectors.toList());
    }

    /**
     * Enhance regions with CLIP classifications
     */
    private List<EnhancedDesignRegion> enhanceWithClassifications(
            List<EnhancedDesignRegion> regions,
            List<CLIPDesignDetectionService.DesignClassification> classifications) {
        
        List<EnhancedDesignRegion> enhancedRegions = new ArrayList<>();
        
        for (int i = 0; i < regions.size() && i < classifications.size(); i++) {
            EnhancedDesignRegion region = regions.get(i);
            CLIPDesignDetectionService.DesignClassification classification = classifications.get(i);
            
            // Create enhanced region with CLIP classification
            EnhancedDesignRegion enhanced = new EnhancedDesignRegion(
                region.getX(), region.getY(), region.getWidth(), region.getHeight(),
                region.getConfidence(), region.getClassName(), region.getDetectionMethod(),
                region.getImage()
            );
            
            enhanced.setClipCategory(classification.getCategory());
            enhanced.setClipConfidence(classification.getConfidence());
            enhanced.setEmbedding(classification.getEmbedding());
            
            enhancedRegions.add(enhanced);
        }
        
        return enhancedRegions;
    }

    /**
     * Validate and filter regions based on quality criteria
     */
    private List<EnhancedDesignRegion> validateAndFilterRegions(List<EnhancedDesignRegion> regions, BufferedImage image) {
        return regions.stream()
            .filter(region -> isValidRegion(region, image))
            .filter(region -> hasMinimumQuality(region))
            .sorted((a, b) -> Double.compare(b.getConfidence(), a.getConfidence())) // Sort by confidence
            .collect(Collectors.toList());
    }

    /**
     * Check if region is valid (within image bounds, reasonable size)
     */
    private boolean isValidRegion(EnhancedDesignRegion region, BufferedImage image) {
        // Check bounds
        if (region.getX() < 0 || region.getY() < 0 || 
            region.getX() + region.getWidth() > image.getWidth() ||
            region.getY() + region.getHeight() > image.getHeight()) {
            return false;
        }
        
        // Check minimum size (at least 20x20 pixels)
        if (region.getWidth() < 20 || region.getHeight() < 20) {
            return false;
        }
        
        // Check maximum size (not more than 80% of image)
        double maxWidth = image.getWidth() * 0.8;
        double maxHeight = image.getHeight() * 0.8;
        if (region.getWidth() > maxWidth || region.getHeight() > maxHeight) {
            return false;
        }
        
        return true;
    }

    /**
     * Check if region has minimum quality
     */
    private boolean hasMinimumQuality(EnhancedDesignRegion region) {
        // Minimum confidence threshold
        if (region.getConfidence() < 0.3) {
            return false;
        }
        
        // Minimum CLIP confidence if available
        if (region.getClipConfidence() != null && region.getClipConfidence() < 0.2) {
            return false;
        }
        
        return true;
    }

    /**
     * Enhanced Design Region with additional metadata
     */
    public static class EnhancedDesignRegion {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final double confidence;
        private final String className;
        private final String detectionMethod;
        private final BufferedImage image;
        
        // CLIP enhancement
        private String clipCategory;
        private Double clipConfidence;
        private float[] embedding;
        
        // Quality metrics
        private Double qualityScore;
        private Map<String, Object> metadata;

        public EnhancedDesignRegion(int x, int y, int width, int height, double confidence, 
                                  String className, String detectionMethod, BufferedImage image) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.confidence = confidence;
            this.className = className;
            this.detectionMethod = detectionMethod;
            this.image = image;
            this.metadata = new HashMap<>();
        }

        // Getters
        public int getX() { return x; }
        public int getY() { return y; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public double getConfidence() { return confidence; }
        public String getClassName() { return className; }
        public String getDetectionMethod() { return detectionMethod; }
        public BufferedImage getImage() { return image; }
        public String getClipCategory() { return clipCategory; }
        public Double getClipConfidence() { return clipConfidence; }
        public float[] getEmbedding() { return embedding; }
        public Double getQualityScore() { return qualityScore; }
        public Map<String, Object> getMetadata() { return metadata; }

        // Setters
        public void setClipCategory(String clipCategory) { this.clipCategory = clipCategory; }
        public void setClipConfidence(Double clipConfidence) { this.clipConfidence = clipConfidence; }
        public void setEmbedding(float[] embedding) { this.embedding = embedding; }
        public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
}
