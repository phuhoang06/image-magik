package com.example.imageservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Service for extracting design regions from mockup images
 * Converts detected regions into individual design images
 */
@Service
@Slf4j
public class DesignExtractionService {

    /**
     * Extract design regions from image
     * @param imageFile Original mockup image
     * @param regions List of detected design regions
     * @return List of extracted design images
     */
    public List<ExtractedDesign> extractDesigns(MultipartFile imageFile, List<DesignDetectionService.DesignRegion> regions) {
        try {
            log.info("Extracting {} design regions from image: {}", regions.size(), imageFile.getOriginalFilename());
            
            // Convert MultipartFile to BufferedImage
            BufferedImage originalImage = convertToBufferedImage(imageFile);
            
            List<ExtractedDesign> extractedDesigns = new ArrayList<>();
            
            for (int i = 0; i < regions.size(); i++) {
                DesignDetectionService.DesignRegion region = regions.get(i);
                
                try {
                    // Extract region from original image
                    BufferedImage designImage = extractRegion(originalImage, region);
                    
                    // Convert to byte array
                    byte[] imageBytes = convertToByteArray(designImage);
                    
                    // Create extracted design object
                    ExtractedDesign extractedDesign = new ExtractedDesign(
                        "design_" + (i + 1),
                        region.getClassName(),
                        region.getConfidence(),
                        region.getX(),
                        region.getY(),
                        region.getWidth(),
                        region.getHeight(),
                        imageBytes,
                        "image/png"
                    );
                    
                    extractedDesigns.add(extractedDesign);
                    log.debug("Extracted design {}: {}x{} at ({}, {})", 
                        i + 1, region.getWidth(), region.getHeight(), region.getX(), region.getY());
                    
                } catch (Exception e) {
                    log.error("Failed to extract design region {}: {}", i + 1, e.getMessage());
                }
            }
            
            log.info("Successfully extracted {} design images", extractedDesigns.size());
            return extractedDesigns;
            
        } catch (Exception e) {
            log.error("Error extracting designs from image", e);
            throw new RuntimeException("Design extraction failed", e);
        }
    }

    /**
     * Convert MultipartFile to BufferedImage
     */
    private BufferedImage convertToBufferedImage(MultipartFile file) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(file.getBytes())) {
            return ImageIO.read(bis);
        }
    }

    /**
     * Extract region from image
     */
    private BufferedImage extractRegion(BufferedImage image, DesignDetectionService.DesignRegion region) {
        // Validate and clamp coordinates
        int x = Math.max(0, Math.min(region.getX(), image.getWidth() - 1));
        int y = Math.max(0, Math.min(region.getY(), image.getHeight() - 1));
        int width = Math.max(1, Math.min(region.getWidth(), image.getWidth() - x));
        int height = Math.max(1, Math.min(region.getHeight(), image.getHeight() - y));
        
        // Create cropped image
        BufferedImage croppedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = croppedImage.createGraphics();
        g2d.drawImage(image, 0, 0, width, height, x, y, x + width, y + height, null);
        g2d.dispose();
        
        return croppedImage;
    }

    /**
     * Convert BufferedImage to byte array
     */
    private byte[] convertToByteArray(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        }
    }

    /**
     * Data class for extracted design
     */
    public static class ExtractedDesign {
        private final String id;
        private final String category;
        private final double confidence;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final byte[] imageData;
        private final String contentType;

        public ExtractedDesign(String id, String category, double confidence, 
                             int x, int y, int width, int height, 
                             byte[] imageData, String contentType) {
            this.id = id;
            this.category = category;
            this.confidence = confidence;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.imageData = imageData;
            this.contentType = contentType;
        }

        // Getters
        public String getId() { return id; }
        public String getCategory() { return category; }
        public double getConfidence() { return confidence; }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public byte[] getImageData() { return imageData; }
        public String getContentType() { return contentType; }
    }
}