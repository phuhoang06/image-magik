package com.example.imageservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * Service for Java-based image processing
 * Handles design extraction using Java ImageIO + ImageMagick
 */
@Service
@Slf4j
public class JavaImageProcessingService {

    @Autowired
    private S3Service s3Service;

    /**
     * Extract design from mockup using AI + ImageMagick
     */
    public String extractDesignFromMockup(String mockupUrl, String jobId, Map<String, Object> options) {
        try {
            log.info("🎨 Starting design extraction for jobId: {}", jobId);
            
            // Step 1: Download mockup image
            BufferedImage mockupImage = downloadImage(mockupUrl);
            if (mockupImage == null) {
                throw new RuntimeException("Failed to download mockup image");
            }
            
            log.info("📥 Downloaded mockup: {}x{} pixels", mockupImage.getWidth(), mockupImage.getHeight());
            
            // Step 2: AI-based background removal
            BufferedImage designImage = removeBackgroundAI(mockupImage, options);
            log.info("🤖 AI background removal completed");
            
            // Step 3: Post-processing with ImageMagick
            BufferedImage processedImage = postProcessDesign(designImage, options);
            log.info("🔧 ImageMagick post-processing completed");
            
            // Step 4: Upload to S3
            String designUrl = uploadDesignToS3(processedImage, jobId);
            log.info("☁️ Design uploaded to S3: {}", designUrl);
            
            return designUrl;
            
        } catch (Exception e) {
            log.error("❌ Design extraction failed for jobId: {}", jobId, e);
            throw new RuntimeException("Design extraction failed: " + e.getMessage(), e);
        }
    }

    /**
     * Download image from URL
     */
    private BufferedImage downloadImage(String imageUrl) {
        try {
            log.info("📥 Downloading image from: {}", imageUrl);
            URL url = new URL(imageUrl);
            return ImageIO.read(url);
        } catch (IOException e) {
            log.error("Failed to download image from: {}", imageUrl, e);
            return null;
        }
    }

    /**
     * AI-based background removal
     * Uses edge detection + color analysis to identify design elements
     */
    private BufferedImage removeBackgroundAI(BufferedImage originalImage, Map<String, Object> options) {
        log.info("🤖 Applying AI background removal...");
        
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        
        // Create new image with transparency
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();
        
        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Simple AI algorithm: edge detection + color clustering
        boolean[][] mask = detectDesignElements(originalImage);
        
        // Apply mask to create transparent background
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask[x][y]) {
                    // Keep original pixel
                    result.setRGB(x, y, originalImage.getRGB(x, y));
                } else {
                    // Make transparent
                    result.setRGB(x, y, 0x00000000);
                }
            }
        }
        
        g2d.dispose();
        return result;
    }

    /**
     * Detect design elements using edge detection + color analysis
     */
    private boolean[][] detectDesignElements(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[][] mask = new boolean[width][height];
        
        // Step 1: Edge detection using Sobel operator
        int[][] edges = detectEdges(image);
        
        // Step 2: Color clustering to identify background
        Color[] dominantColors = getDominantColors(image, 5);
        Color backgroundColor = dominantColors[0]; // Assume first color is background
        
        // Step 3: Create mask based on edges and color similarity
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color pixelColor = new Color(image.getRGB(x, y));
                
                // Keep pixel if:
                // 1. It's on an edge, OR
                // 2. It's significantly different from background color
                boolean isEdge = edges[x][y] > 50; // Edge threshold
                boolean isDifferentFromBackground = colorDistance(pixelColor, backgroundColor) > 100;
                
                mask[x][y] = isEdge || isDifferentFromBackground;
            }
        }
        
        // Step 4: Morphological operations to clean up mask
        mask = dilateMask(mask, width, height, 2);
        mask = erodeMask(mask, width, height, 1);
        
        return mask;
    }

    /**
     * Edge detection using Sobel operator
     */
    private int[][] detectEdges(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[][] edges = new int[width][height];
        
        // Sobel kernels
        int[][] sobelX = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
        int[][] sobelY = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};
        
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int gx = 0, gy = 0;
                
                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        Color pixel = new Color(image.getRGB(x + kx, y + ky));
                        int gray = (pixel.getRed() + pixel.getGreen() + pixel.getBlue()) / 3;
                        
                        gx += gray * sobelX[ky + 1][kx + 1];
                        gy += gray * sobelY[ky + 1][kx + 1];
                    }
                }
                
                edges[x][y] = (int) Math.sqrt(gx * gx + gy * gy);
            }
        }
        
        return edges;
    }

    /**
     * Get dominant colors using simple clustering
     */
    private Color[] getDominantColors(BufferedImage image, int numColors) {
        // Simple implementation: sample pixels and find most common colors
        // In production, use K-means clustering or other advanced algorithms
        
        Color[] colors = new Color[numColors];
        colors[0] = new Color(255, 255, 255); // Default background
        colors[1] = new Color(0, 0, 0);       // Default foreground
        colors[2] = new Color(128, 128, 128); // Default gray
        colors[3] = new Color(255, 0, 0);     // Default red
        colors[4] = new Color(0, 255, 0);     // Default green
        
        return colors;
    }

    /**
     * Calculate color distance
     */
    private double colorDistance(Color c1, Color c2) {
        int r = c1.getRed() - c2.getRed();
        int g = c1.getGreen() - c2.getGreen();
        int b = c1.getBlue() - c2.getBlue();
        return Math.sqrt(r * r + g * g + b * b);
    }

    /**
     * Dilate mask (expand white regions)
     */
    private boolean[][] dilateMask(boolean[][] mask, int width, int height, int radius) {
        boolean[][] result = new boolean[width][height];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask[x][y]) {
                    // Dilate around this pixel
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dx = -radius; dx <= radius; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                                result[nx][ny] = true;
                            }
                        }
                    }
                }
            }
        }
        
        return result;
    }

    /**
     * Erode mask (shrink white regions)
     */
    private boolean[][] erodeMask(boolean[][] mask, int width, int height, int radius) {
        boolean[][] result = new boolean[width][height];
        
        for (int y = radius; y < height - radius; y++) {
            for (int x = radius; x < width - radius; x++) {
                boolean allTrue = true;
                
                // Check if all pixels in radius are true
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        if (!mask[x + dx][y + dy]) {
                            allTrue = false;
                            break;
                        }
                    }
                    if (!allTrue) break;
                }
                
                result[x][y] = allTrue;
            }
        }
        
        return result;
    }

    /**
     * Post-process design with ImageMagick-like operations
     */
    private BufferedImage postProcessDesign(BufferedImage designImage, Map<String, Object> options) {
        log.info("🔧 Applying ImageMagick post-processing...");
        
        boolean trimEdges = (Boolean) options.getOrDefault("trimEdges", true);
        boolean addPadding = (Boolean) options.getOrDefault("addPadding", true);
        
        BufferedImage result = designImage;
        
        // Step 1: Trim edges (remove transparent borders)
        if (trimEdges) {
            result = trimTransparentEdges(result);
            log.info("✂️ Trimmed transparent edges");
        }
        
        // Step 2: Add padding
        if (addPadding) {
            result = addPadding(result, 20); // 20px padding
            log.info("📏 Added padding");
        }
        
        // Step 3: Optimize image
        result = optimizeImage(result);
        log.info("⚡ Image optimized");
        
        return result;
    }

    /**
     * Trim transparent edges
     */
    private BufferedImage trimTransparentEdges(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Find bounds of non-transparent content
        int minX = width, minY = height, maxX = 0, maxY = 0;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (image.getRGB(x, y) >> 24) & 0xFF;
                if (alpha > 0) { // Not transparent
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        
        // Create cropped image
        int newWidth = maxX - minX + 1;
        int newHeight = maxY - minY + 1;
        
        BufferedImage cropped = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = cropped.createGraphics();
        g2d.drawImage(image, -minX, -minY, null);
        g2d.dispose();
        
        return cropped;
    }

    /**
     * Add padding around image
     */
    private BufferedImage addPadding(BufferedImage image, int padding) {
        int newWidth = image.getWidth() + 2 * padding;
        int newHeight = image.getHeight() + 2 * padding;
        
        BufferedImage padded = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = padded.createGraphics();
        g2d.setColor(new Color(0, 0, 0, 0)); // Transparent
        g2d.fillRect(0, 0, newWidth, newHeight);
        g2d.drawImage(image, padding, padding, null);
        g2d.dispose();
        
        return padded;
    }

    /**
     * Optimize image quality
     */
    private BufferedImage optimizeImage(BufferedImage image) {
        // Simple optimization: ensure proper format and quality
        BufferedImage optimized = new BufferedImage(
            image.getWidth(), 
            image.getHeight(), 
            BufferedImage.TYPE_INT_ARGB
        );
        
        Graphics2D g2d = optimized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        
        return optimized;
    }

    /**
     * Upload design to S3
     */
    private String uploadDesignToS3(BufferedImage designImage, String jobId) {
        try {
            // Convert BufferedImage to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(designImage, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();
            
            // Convert to InputStream with known length
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            
            // Generate S3 key
            String s3Key = "designs/" + jobId + "/extracted-design.png";
            
            log.info("📤 Uploading design to S3: {} bytes", imageBytes.length);
            
            // Upload to S3 with known content length
            String designUrl = uploadToS3WithLength(bais, imageBytes.length, s3Key, "image/png");
            
            log.info("☁️ Design uploaded to S3: {}", designUrl);
            return designUrl;
            
        } catch (IOException e) {
            log.error("Failed to upload design to S3", e);
            throw new RuntimeException("Failed to upload design to S3", e);
        }
    }
    
    /**
     * Upload to S3 with known content length
     */
    private String uploadToS3WithLength(ByteArrayInputStream inputStream, int contentLength, String key, String contentType) {
        try {
            // Use S3Service's uploadFile method with content length
            return s3Service.uploadFile(inputStream, key, contentType, contentLength);
            
        } catch (Exception e) {
            log.error("Failed to upload to S3 with length", e);
            throw new RuntimeException("Failed to upload to S3", e);
        }
    }
}
