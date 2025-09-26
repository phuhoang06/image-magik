package com.example.imageservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Generic Design Detection Service
 * Detects design regions using edge detection, color analysis, and texture analysis
 * Works with any design without requiring training
 */
@Service
@Slf4j
public class GenericDesignDetectionService {

    // Configuration parameters
    private static final int MIN_DESIGN_SIZE = 50;  // Minimum design region size
    private static final int MAX_DESIGN_SIZE = 400; // Maximum design region size
    private static final double MIN_CONTRAST_RATIO = 0.3; // Minimum contrast ratio
    private static final double MIN_EDGE_DENSITY = 0.1;   // Minimum edge density

    /**
     * Detect design regions using multiple approaches
     */
    public List<DesignRegion> detectDesigns(BufferedImage image) {
        log.info("Starting generic design detection for image: {}x{}", image.getWidth(), image.getHeight());
        
        List<DesignRegion> results = new ArrayList<>();
        
        try {
            // 1. Edge-based detection
            List<DesignRegion> edgeRegions = detectByEdges(image);
            log.info("Edge-based detection found {} regions", edgeRegions.size());
            
            // 2. Color-based detection
            List<DesignRegion> colorRegions = detectByColor(image);
            log.info("Color-based detection found {} regions", colorRegions.size());
            
            // 3. Texture-based detection
            List<DesignRegion> textureRegions = detectByTexture(image);
            log.info("Texture-based detection found {} regions", textureRegions.size());
            
            // 4. Merge and filter results
            results = mergeAndFilterResults(edgeRegions, colorRegions, textureRegions);
            log.info("Final merged results: {} design regions", results.size());
            
        } catch (Exception e) {
            log.error("Error during generic design detection", e);
        }
        
        return results;
    }

    /**
     * Detect design regions using edge detection
     */
    private List<DesignRegion> detectByEdges(BufferedImage image) {
        List<DesignRegion> regions = new ArrayList<>();
        
        try {
            // Convert to grayscale
            BufferedImage grayImage = convertToGrayscale(image);
            
            // Apply edge detection (simplified Canny-like)
            BufferedImage edgeImage = detectEdges(grayImage);
            
            // Find contours and create bounding boxes
            regions = findContours(edgeImage);
            
            // Filter by edge density
            regions = filterByEdgeDensity(regions, edgeImage);
            
        } catch (Exception e) {
            log.error("Error in edge-based detection", e);
        }
        
        return regions;
    }

    /**
     * Detect design regions using color analysis
     */
    private List<DesignRegion> detectByColor(BufferedImage image) {
        List<DesignRegion> regions = new ArrayList<>();
        
        try {
            // Analyze color distribution
            Map<Color, Integer> colorHistogram = analyzeColorHistogram(image);
            
            // Find dominant colors (background)
            List<Color> dominantColors = findDominantColors(colorHistogram, 3);
            
            // Detect regions with different colors
            regions = detectColorRegions(image, dominantColors);
            
            // Filter by contrast ratio
            regions = filterByContrast(regions, image);
            
        } catch (Exception e) {
            log.error("Error in color-based detection", e);
        }
        
        return regions;
    }

    /**
     * Detect design regions using texture analysis
     */
    private List<DesignRegion> detectByTexture(BufferedImage image) {
        List<DesignRegion> regions = new ArrayList<>();
        
        try {
            // Convert to grayscale
            BufferedImage grayImage = convertToGrayscale(image);
            
            // Apply texture analysis (simplified Gabor-like)
            BufferedImage textureImage = analyzeTexture(grayImage);
            
            // Find texture regions
            regions = findTextureRegions(textureImage);
            
            // Filter by texture variance
            regions = filterByTextureVariance(regions, grayImage);
            
        } catch (Exception e) {
            log.error("Error in texture-based detection", e);
        }
        
        return regions;
    }

    /**
     * Convert image to grayscale
     */
    private BufferedImage convertToGrayscale(BufferedImage image) {
        BufferedImage grayImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = grayImage.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        return grayImage;
    }

    /**
     * Detect edges using simplified Canny-like algorithm
     */
    private BufferedImage detectEdges(BufferedImage grayImage) {
        int width = grayImage.getWidth();
        int height = grayImage.getHeight();
        BufferedImage edgeImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        
        // Sobel edge detection
        int[][] sobelX = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
        int[][] sobelY = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};
        
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int gx = 0, gy = 0;
                
                // Apply Sobel operators
                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        int pixel = new Color(grayImage.getRGB(x + kx, y + ky)).getRed();
                        gx += pixel * sobelX[ky + 1][kx + 1];
                        gy += pixel * sobelY[ky + 1][kx + 1];
                    }
                }
                
                int magnitude = (int) Math.sqrt(gx * gx + gy * gy);
                magnitude = Math.min(255, magnitude);
                
                edgeImage.setRGB(x, y, new Color(magnitude, magnitude, magnitude).getRGB());
            }
        }
        
        return edgeImage;
    }

    /**
     * Find contours and create bounding boxes
     */
    private List<DesignRegion> findContours(BufferedImage edgeImage) {
        List<DesignRegion> regions = new ArrayList<>();
        
        // Simplified contour finding
        int width = edgeImage.getWidth();
        int height = edgeImage.getHeight();
        boolean[][] visited = new boolean[height][width];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!visited[y][x] && isEdgePixel(edgeImage, x, y)) {
                    // Find connected edge pixels
                    Rectangle bounds = floodFill(edgeImage, x, y, visited);
                    if (bounds != null && isValidRegion(bounds)) {
                        regions.add(new DesignRegion(
                            bounds.x, bounds.y, bounds.width, bounds.height,
                            "edge_detected", 0.8
                        ));
                    }
                }
            }
        }
        
        return regions;
    }

    /**
     * Check if pixel is an edge pixel
     */
    private boolean isEdgePixel(BufferedImage edgeImage, int x, int y) {
        if (x < 0 || x >= edgeImage.getWidth() || y < 0 || y >= edgeImage.getHeight()) {
            return false;
        }
        Color color = new Color(edgeImage.getRGB(x, y));
        return color.getRed() > 128; // Threshold for edge pixels
    }

    /**
     * Flood fill to find connected edge pixels
     */
    private Rectangle floodFill(BufferedImage edgeImage, int startX, int startY, boolean[][] visited) {
        int width = edgeImage.getWidth();
        int height = edgeImage.getHeight();
        
        int minX = startX, maxX = startX;
        int minY = startY, maxY = startY;
        
        List<Point> stack = new ArrayList<>();
        stack.add(new Point(startX, startY));
        
        while (!stack.isEmpty()) {
            Point p = stack.remove(stack.size() - 1);
            int x = p.x, y = p.y;
            
            if (x < 0 || x >= width || y < 0 || y >= height || visited[y][x]) {
                continue;
            }
            
            if (!isEdgePixel(edgeImage, x, y)) {
                continue;
            }
            
            visited[y][x] = true;
            
            // Update bounds
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            
            // Add neighbors
            stack.add(new Point(x + 1, y));
            stack.add(new Point(x - 1, y));
            stack.add(new Point(x, y + 1));
            stack.add(new Point(x, y - 1));
        }
        
        if (maxX - minX > 10 && maxY - minY > 10) { // Minimum size
            return new Rectangle(minX, minY, maxX - minX, maxY - minY);
        }
        
        return null;
    }

    /**
     * Analyze color histogram
     */
    private Map<Color, Integer> analyzeColorHistogram(BufferedImage image) {
        Map<Color, Integer> histogram = new ConcurrentHashMap<>();
        
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Sample every 4th pixel for performance
        for (int y = 0; y < height; y += 4) {
            for (int x = 0; x < width; x += 4) {
                Color color = new Color(image.getRGB(x, y));
                // Quantize colors to reduce noise
                Color quantized = quantizeColor(color);
                histogram.put(quantized, histogram.getOrDefault(quantized, 0) + 1);
            }
        }
        
        return histogram;
    }

    /**
     * Quantize color to reduce noise
     */
    private Color quantizeColor(Color color) {
        int r = (color.getRed() / 32) * 32;
        int g = (color.getGreen() / 32) * 32;
        int b = (color.getBlue() / 32) * 32;
        return new Color(r, g, b);
    }

    /**
     * Find dominant colors
     */
    private List<Color> findDominantColors(Map<Color, Integer> histogram, int count) {
        return histogram.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(count)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Detect regions with different colors
     */
    private List<DesignRegion> detectColorRegions(BufferedImage image, List<Color> dominantColors) {
        List<DesignRegion> regions = new ArrayList<>();
        
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[][] visited = new boolean[height][width];
        
        for (int y = 0; y < height; y += 8) { // Sample every 8th pixel
            for (int x = 0; x < width; x += 8) {
                if (!visited[y][x]) {
                    Color pixelColor = new Color(image.getRGB(x, y));
                    if (!isDominantColor(pixelColor, dominantColors)) {
                        // Found non-dominant color, find region
                        Rectangle bounds = floodFillColor(image, x, y, visited, dominantColors);
                        if (bounds != null && isValidRegion(bounds)) {
                            regions.add(new DesignRegion(
                                bounds.x, bounds.y, bounds.width, bounds.height,
                                "color_detected", 0.7
                            ));
                        }
                    }
                }
            }
        }
        
        return regions;
    }

    /**
     * Check if color is dominant
     */
    private boolean isDominantColor(Color color, List<Color> dominantColors) {
        for (Color dominant : dominantColors) {
            if (colorDistance(color, dominant) < 50) { // Threshold for color similarity
                return true;
            }
        }
        return false;
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
     * Flood fill for color regions
     */
    private Rectangle floodFillColor(BufferedImage image, int startX, int startY, 
                                   boolean[][] visited, List<Color> dominantColors) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        int minX = startX, maxX = startX;
        int minY = startY, maxY = startY;
        
        List<Point> stack = new ArrayList<>();
        stack.add(new Point(startX, startY));
        
        while (!stack.isEmpty()) {
            Point p = stack.remove(stack.size() - 1);
            int x = p.x, y = p.y;
            
            if (x < 0 || x >= width || y < 0 || y >= height || visited[y][x]) {
                continue;
            }
            
            Color pixelColor = new Color(image.getRGB(x, y));
            if (isDominantColor(pixelColor, dominantColors)) {
                continue;
            }
            
            visited[y][x] = true;
            
            // Update bounds
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            
            // Add neighbors
            stack.add(new Point(x + 1, y));
            stack.add(new Point(x - 1, y));
            stack.add(new Point(x, y + 1));
            stack.add(new Point(x, y - 1));
        }
        
        if (maxX - minX > 20 && maxY - minY > 20) { // Minimum size
            return new Rectangle(minX, minY, maxX - minX, maxY - minY);
        }
        
        return null;
    }

    /**
     * Analyze texture using simplified approach
     */
    private BufferedImage analyzeTexture(BufferedImage grayImage) {
        int width = grayImage.getWidth();
        int height = grayImage.getHeight();
        BufferedImage textureImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        
        // Calculate local variance as texture measure
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                double variance = calculateLocalVariance(grayImage, x, y, 3);
                int textureValue = (int) Math.min(255, variance * 10);
                textureImage.setRGB(x, y, new Color(textureValue, textureValue, textureValue).getRGB());
            }
        }
        
        return textureImage;
    }

    /**
     * Calculate local variance
     */
    private double calculateLocalVariance(BufferedImage image, int centerX, int centerY, int windowSize) {
        double sum = 0, sumSquared = 0;
        int count = 0;
        
        for (int y = centerY - windowSize/2; y <= centerY + windowSize/2; y++) {
            for (int x = centerX - windowSize/2; x <= centerX + windowSize/2; x++) {
                if (x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight()) {
                    int pixel = new Color(image.getRGB(x, y)).getRed();
                    sum += pixel;
                    sumSquared += pixel * pixel;
                    count++;
                }
            }
        }
        
        if (count == 0) return 0;
        
        double mean = sum / count;
        double variance = (sumSquared / count) - (mean * mean);
        return Math.max(0, variance);
    }

    /**
     * Find texture regions
     */
    private List<DesignRegion> findTextureRegions(BufferedImage textureImage) {
        List<DesignRegion> regions = new ArrayList<>();
        
        // Similar to edge detection but for texture
        int width = textureImage.getWidth();
        int height = textureImage.getHeight();
        boolean[][] visited = new boolean[height][width];
        
        for (int y = 0; y < height; y += 4) {
            for (int x = 0; x < width; x += 4) {
                if (!visited[y][x] && isHighTexture(textureImage, x, y)) {
                    Rectangle bounds = floodFillTexture(textureImage, x, y, visited);
                    if (bounds != null && isValidRegion(bounds)) {
                        regions.add(new DesignRegion(
                            bounds.x, bounds.y, bounds.width, bounds.height,
                            "texture_detected", 0.6
                        ));
                    }
                }
            }
        }
        
        return regions;
    }

    /**
     * Check if pixel has high texture
     */
    private boolean isHighTexture(BufferedImage textureImage, int x, int y) {
        if (x < 0 || x >= textureImage.getWidth() || y < 0 || y >= textureImage.getHeight()) {
            return false;
        }
        Color color = new Color(textureImage.getRGB(x, y));
        return color.getRed() > 100; // Threshold for high texture
    }

    /**
     * Flood fill for texture regions
     */
    private Rectangle floodFillTexture(BufferedImage textureImage, int startX, int startY, boolean[][] visited) {
        int width = textureImage.getWidth();
        int height = textureImage.getHeight();
        
        int minX = startX, maxX = startX;
        int minY = startY, maxY = startY;
        
        List<Point> stack = new ArrayList<>();
        stack.add(new Point(startX, startY));
        
        while (!stack.isEmpty()) {
            Point p = stack.remove(stack.size() - 1);
            int x = p.x, y = p.y;
            
            if (x < 0 || x >= width || y < 0 || y >= height || visited[y][x]) {
                continue;
            }
            
            if (!isHighTexture(textureImage, x, y)) {
                continue;
            }
            
            visited[y][x] = true;
            
            // Update bounds
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            
            // Add neighbors
            stack.add(new Point(x + 1, y));
            stack.add(new Point(x - 1, y));
            stack.add(new Point(x, y + 1));
            stack.add(new Point(x, y - 1));
        }
        
        if (maxX - minX > 15 && maxY - minY > 15) { // Minimum size
            return new Rectangle(minX, minY, maxX - minX, maxY - minY);
        }
        
        return null;
    }

    /**
     * Filter regions by edge density
     */
    private List<DesignRegion> filterByEdgeDensity(List<DesignRegion> regions, BufferedImage edgeImage) {
        return regions.stream()
                .filter(region -> calculateEdgeDensity(region, edgeImage) > MIN_EDGE_DENSITY)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Calculate edge density for a region
     */
    private double calculateEdgeDensity(DesignRegion region, BufferedImage edgeImage) {
        int edgePixels = 0;
        int totalPixels = 0;
        
        for (int y = region.getY(); y < region.getY() + region.getHeight(); y++) {
            for (int x = region.getX(); x < region.getX() + region.getWidth(); x++) {
                if (x >= 0 && x < edgeImage.getWidth() && y >= 0 && y < edgeImage.getHeight()) {
                    totalPixels++;
                    if (isEdgePixel(edgeImage, x, y)) {
                        edgePixels++;
                    }
                }
            }
        }
        
        return totalPixels > 0 ? (double) edgePixels / totalPixels : 0;
    }

    /**
     * Filter regions by contrast
     */
    private List<DesignRegion> filterByContrast(List<DesignRegion> regions, BufferedImage image) {
        return regions.stream()
                .filter(region -> calculateContrast(region, image) > MIN_CONTRAST_RATIO)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Calculate contrast ratio for a region
     */
    private double calculateContrast(DesignRegion region, BufferedImage image) {
        // Simplified contrast calculation
        int minGray = 255, maxGray = 0;
        
        for (int y = region.getY(); y < region.getY() + region.getHeight(); y++) {
            for (int x = region.getX(); x < region.getX() + region.getWidth(); x++) {
                if (x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight()) {
                    Color color = new Color(image.getRGB(x, y));
                    int gray = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                    minGray = Math.min(minGray, gray);
                    maxGray = Math.max(maxGray, gray);
                }
            }
        }
        
        return maxGray > 0 ? (double) (maxGray - minGray) / maxGray : 0;
    }

    /**
     * Filter regions by texture variance
     */
    private List<DesignRegion> filterByTextureVariance(List<DesignRegion> regions, BufferedImage grayImage) {
        return regions.stream()
                .filter(region -> calculateTextureVariance(region, grayImage) > 50)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Calculate texture variance for a region
     */
    private double calculateTextureVariance(DesignRegion region, BufferedImage grayImage) {
        double sum = 0, sumSquared = 0;
        int count = 0;
        
        for (int y = region.getY(); y < region.getY() + region.getHeight(); y++) {
            for (int x = region.getX(); x < region.getX() + region.getWidth(); x++) {
                if (x >= 0 && x < grayImage.getWidth() && y >= 0 && y < grayImage.getHeight()) {
                    int pixel = new Color(grayImage.getRGB(x, y)).getRed();
                    sum += pixel;
                    sumSquared += pixel * pixel;
                    count++;
                }
            }
        }
        
        if (count == 0) return 0;
        
        double mean = sum / count;
        double variance = (sumSquared / count) - (mean * mean);
        return variance;
    }

    /**
     * Check if region is valid
     */
    private boolean isValidRegion(Rectangle bounds) {
        return bounds.width >= MIN_DESIGN_SIZE && bounds.width <= MAX_DESIGN_SIZE &&
               bounds.height >= MIN_DESIGN_SIZE && bounds.height <= MAX_DESIGN_SIZE;
    }

    /**
     * Merge and filter results from different detection methods
     */
    private List<DesignRegion> mergeAndFilterResults(List<DesignRegion> edgeRegions, 
                                                   List<DesignRegion> colorRegions, 
                                                   List<DesignRegion> textureRegions) {
        List<DesignRegion> allRegions = new ArrayList<>();
        allRegions.addAll(edgeRegions);
        allRegions.addAll(colorRegions);
        allRegions.addAll(textureRegions);
        
        // Remove overlapping regions
        return removeOverlappingRegions(allRegions);
    }

    /**
     * Remove overlapping regions
     */
    private List<DesignRegion> removeOverlappingRegions(List<DesignRegion> regions) {
        List<DesignRegion> filtered = new ArrayList<>();
        
        for (DesignRegion region : regions) {
            boolean isOverlapping = false;
            
            for (DesignRegion existing : filtered) {
                if (calculateOverlap(region, existing) > 0.5) { // 50% overlap threshold
                    isOverlapping = true;
                    // Keep the one with higher confidence
                    if (region.getConfidence() > existing.getConfidence()) {
                        filtered.remove(existing);
                        filtered.add(region);
                    }
                    break;
                }
            }
            
            if (!isOverlapping) {
                filtered.add(region);
            }
        }
        
        return filtered;
    }

    /**
     * Calculate overlap ratio between two regions
     */
    private double calculateOverlap(DesignRegion region1, DesignRegion region2) {
        int x1 = Math.max(region1.getX(), region2.getX());
        int y1 = Math.max(region1.getY(), region2.getY());
        int x2 = Math.min(region1.getX() + region1.getWidth(), region2.getX() + region2.getWidth());
        int y2 = Math.min(region1.getY() + region1.getHeight(), region2.getY() + region2.getHeight());
        
        if (x2 <= x1 || y2 <= y1) {
            return 0; // No overlap
        }
        
        int overlapArea = (x2 - x1) * (y2 - y1);
        int area1 = region1.getWidth() * region1.getHeight();
        int area2 = region2.getWidth() * region2.getHeight();
        
        return (double) overlapArea / Math.min(area1, area2);
    }

    /**
     * Design region data class
     */
    public static class DesignRegion {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final String label;
        private final double confidence;

        public DesignRegion(int x, int y, int width, int height, String label, double confidence) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.label = label;
            this.confidence = confidence;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public String getLabel() { return label; }
        public double getConfidence() { return confidence; }
    }
}
