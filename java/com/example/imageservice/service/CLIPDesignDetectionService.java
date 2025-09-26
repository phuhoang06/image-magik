package com.example.imageservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CLIP-based Design Detection Service
 * Uses CLIP model for design classification and similarity search
 * Provides semantic understanding of design regions
 */
@Service
@Slf4j
public class CLIPDesignDetectionService {

    @Value("${ai.clip.model-path:models/clip-vit-base-patch32.onnx}")
    private String clipModelPath;
    
    @Value("${ai.clip.embedding-size:512}")
    private int embeddingSize;
    
    @Value("${ai.clip.input-size:224}")
    private int clipInputSize;
    
    @Value("${ai.clip.confidence-threshold:0.3}")
    private double clipConfidenceThreshold;

    // Design categories for classification
    private static final String[] DESIGN_CATEGORIES = {
        "logo", "text", "graphic design", "illustration", "pattern", 
        "symbol", "icon", "artwork", "brand", "decoration"
    };

    // Common design descriptions
    private static final String[] DESIGN_DESCRIPTIONS = {
        "logo with text", "graphic illustration", "decorative pattern", 
        "brand symbol", "artistic design", "text logo", "icon design",
        "graphic element", "visual design", "creative artwork"
    };

    /**
     * Classify design regions using CLIP
     */
    public List<DesignClassification> classifyDesignRegions(BufferedImage image, List<DesignRegion> regions) {
        log.info("Starting CLIP classification for {} regions", regions.size());
        
        List<DesignClassification> classifications = new ArrayList<>();
        
        for (DesignRegion region : regions) {
            try {
                // Validate region before processing
                if (region.getX() < 0 || region.getY() < 0 || 
                    region.getWidth() <= 0 || region.getHeight() <= 0) {
                    log.warn("Skipping invalid region: x={}, y={}, width={}, height={}", 
                        region.getX(), region.getY(), region.getWidth(), region.getHeight());
                    continue;
                }
                
                // Extract region from image
                BufferedImage regionImage = extractRegion(image, region);
                
                // Classify region
                DesignClassification classification = classifyRegion(regionImage, region);
                classifications.add(classification);
                
            } catch (Exception e) {
                log.error("Error processing region: x={}, y={}, width={}, height={}, error={}", 
                    region.getX(), region.getY(), region.getWidth(), region.getHeight(), e.getMessage());
                // Skip this region and continue with others
            }
        }
        
        log.info("CLIP classification completed for {} regions", classifications.size());
        return classifications;
    }

    /**
     * Generate embeddings for design regions
     */
    public List<DesignEmbedding> generateEmbeddings(BufferedImage image, List<DesignRegion> regions) {
        log.info("Generating CLIP embeddings for {} regions", regions.size());
        
        List<DesignEmbedding> embeddings = new ArrayList<>();
        
        try {
            for (DesignRegion region : regions) {
                // Extract region from image
                BufferedImage regionImage = extractRegion(image, region);
                
                // Generate embedding
                float[] embedding = generateRegionEmbedding(regionImage);
                
                embeddings.add(new DesignEmbedding(region, embedding));
            }
            
            log.info("Generated {} CLIP embeddings", embeddings.size());
            
        } catch (Exception e) {
            log.error("Error generating CLIP embeddings", e);
        }
        
        return embeddings;
    }

    /**
     * Find similar designs using CLIP embeddings
     */
    public List<SimilarDesign> findSimilarDesigns(DesignEmbedding queryEmbedding, 
                                                 List<DesignEmbedding> databaseEmbeddings, 
                                                 int topK) {
        log.info("Finding similar designs for query embedding");
        
        List<SimilarDesign> similarDesigns = new ArrayList<>();
        
        try {
            for (DesignEmbedding dbEmbedding : databaseEmbeddings) {
                // Calculate cosine similarity
                double similarity = calculateCosineSimilarity(queryEmbedding.getEmbedding(), 
                                                            dbEmbedding.getEmbedding());
                
                if (similarity > clipConfidenceThreshold) {
                    similarDesigns.add(new SimilarDesign(dbEmbedding.getRegion(), similarity));
                }
            }
            
            // Sort by similarity (highest first)
            similarDesigns.sort((s1, s2) -> Double.compare(s2.getSimilarity(), s1.getSimilarity()));
            
            // Return top K results
            if (similarDesigns.size() > topK) {
                similarDesigns = similarDesigns.subList(0, topK);
            }
            
            log.info("Found {} similar designs", similarDesigns.size());
            
        } catch (Exception e) {
            log.error("Error finding similar designs", e);
        }
        
        return similarDesigns;
    }

    /**
     * Extract region from image
     */
    private BufferedImage extractRegion(BufferedImage image, DesignRegion region) {
        // Validate region coordinates
        int x = Math.max(0, Math.min(region.getX(), image.getWidth() - 1));
        int y = Math.max(0, Math.min(region.getY(), image.getHeight() - 1));
        int width = Math.max(1, Math.min(region.getWidth(), image.getWidth() - x));
        int height = Math.max(1, Math.min(region.getHeight(), image.getHeight() - y));
        
        // Additional validation to prevent negative dimensions
        if (width <= 0 || height <= 0) {
            log.warn("Invalid region dimensions: x={}, y={}, width={}, height={}, image={}x{}", 
                region.getX(), region.getY(), region.getWidth(), region.getHeight(), 
                image.getWidth(), image.getHeight());
            // Return a small default region
            width = Math.min(50, image.getWidth());
            height = Math.min(50, image.getHeight());
            x = Math.min(x, image.getWidth() - width);
            y = Math.min(y, image.getHeight() - height);
        }
        
        log.debug("Extracting region: x={}, y={}, width={}, height={} from image {}x{}", 
            x, y, width, height, image.getWidth(), image.getHeight());
        
        BufferedImage regionImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = regionImage.createGraphics();
        g2d.drawImage(image, 0, 0, width, height, x, y, x + width, y + height, null);
        g2d.dispose();
        
        return regionImage;
    }

    /**
     * Classify a single region using CLIP
     */
    private DesignClassification classifyRegion(BufferedImage regionImage, DesignRegion region) {
        try {
            // Resize image for CLIP input
            BufferedImage resizedImage = resizeImage(regionImage, clipInputSize, clipInputSize);
            
            // Generate embedding
            float[] embedding = generateRegionEmbedding(resizedImage);
            
            // Classify against design categories
            Map<String, Double> categoryScores = classifyAgainstCategories(embedding);
            
            // Find best category
            String bestCategory = findBestCategory(categoryScores);
            double confidence = categoryScores.getOrDefault(bestCategory, 0.0);
            
            return new DesignClassification(region, bestCategory, confidence, embedding);
            
        } catch (Exception e) {
            log.error("Error classifying region", e);
            return new DesignClassification(region, "unknown", 0.0, new float[embeddingSize]);
        }
    }

    /**
     * Generate embedding for a region (Mock implementation)
     */
    private float[] generateRegionEmbedding(BufferedImage image) {
        // Mock CLIP embedding generation
        // In real implementation, this would use ONNX Runtime with CLIP model
        float[] embedding = new float[embeddingSize];
        
        // Simple feature extraction based on image properties
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Color features
        float[] colorFeatures = extractColorFeatures(image);
        System.arraycopy(colorFeatures, 0, embedding, 0, Math.min(colorFeatures.length, embeddingSize));
        
        // Texture features
        float[] textureFeatures = extractTextureFeatures(image);
        System.arraycopy(textureFeatures, 0, embedding, colorFeatures.length, 
                        Math.min(textureFeatures.length, embeddingSize - colorFeatures.length));
        
        // Edge features
        float[] edgeFeatures = extractEdgeFeatures(image);
        System.arraycopy(edgeFeatures, 0, embedding, colorFeatures.length + textureFeatures.length,
                        Math.min(edgeFeatures.length, embeddingSize - colorFeatures.length - textureFeatures.length));
        
        // Normalize embedding
        normalizeEmbedding(embedding);
        
        return embedding;
    }

    /**
     * Extract color features
     */
    private float[] extractColorFeatures(BufferedImage image) {
        float[] features = new float[64]; // 64 color features
        
        int width = image.getWidth();
        int height = image.getHeight();
        int totalPixels = width * height;
        
        if (totalPixels == 0) return features;
        
        // Color histogram
        int[] rHist = new int[16];
        int[] gHist = new int[16];
        int[] bHist = new int[16];
        int[] grayHist = new int[16];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color color = new Color(image.getRGB(x, y));
                rHist[color.getRed() / 16]++;
                gHist[color.getGreen() / 16]++;
                bHist[color.getBlue() / 16]++;
                int gray = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                grayHist[gray / 16]++;
            }
        }
        
        // Convert to features
        for (int i = 0; i < 16; i++) {
            features[i] = (float) rHist[i] / totalPixels;
            features[i + 16] = (float) gHist[i] / totalPixels;
            features[i + 32] = (float) bHist[i] / totalPixels;
            features[i + 48] = (float) grayHist[i] / totalPixels;
        }
        
        return features;
    }

    /**
     * Extract texture features
     */
    private float[] extractTextureFeatures(BufferedImage image) {
        float[] features = new float[32]; // 32 texture features
        
        int width = image.getWidth();
        int height = image.getHeight();
        
        if (width < 3 || height < 3) return features;
        
        // Local Binary Pattern (simplified)
        float[] lbpFeatures = new float[8];
        float[] gradientFeatures = new float[8];
        float[] varianceFeatures = new float[8];
        float[] contrastFeatures = new float[8];
        
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                // LBP
                int centerGray = getGrayValue(image, x, y);
                for (int i = 0; i < 8; i++) {
                    int neighborGray = getNeighborGray(image, x, y, i);
                    if (neighborGray > centerGray) {
                        lbpFeatures[i]++;
                    }
                }
                
                // Gradient
                int gx = getGrayValue(image, x + 1, y) - getGrayValue(image, x - 1, y);
                int gy = getGrayValue(image, x, y + 1) - getGrayValue(image, x, y - 1);
                double magnitude = Math.sqrt(gx * gx + gy * gy);
                gradientFeatures[(int) (magnitude / 32) % 8]++;
                
                // Variance
                double variance = calculateLocalVariance(image, x, y, 3);
                varianceFeatures[(int) (variance / 100) % 8]++;
                
                // Contrast
                int maxGray = 0, minGray = 255;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int gray = getGrayValue(image, x + dx, y + dy);
                        maxGray = Math.max(maxGray, gray);
                        minGray = Math.min(minGray, gray);
                    }
                }
                int contrast = maxGray - minGray;
                contrastFeatures[contrast / 32]++;
            }
        }
        
        // Normalize features
        int totalPixels = (width - 2) * (height - 2);
        for (int i = 0; i < 8; i++) {
            features[i] = lbpFeatures[i] / totalPixels;
            features[i + 8] = gradientFeatures[i] / totalPixels;
            features[i + 16] = varianceFeatures[i] / totalPixels;
            features[i + 24] = contrastFeatures[i] / totalPixels;
        }
        
        return features;
    }

    /**
     * Extract edge features
     */
    private float[] extractEdgeFeatures(BufferedImage image) {
        float[] features = new float[16]; // 16 edge features
        
        int width = image.getWidth();
        int height = image.getHeight();
        
        if (width < 3 || height < 3) return features;
        
        // Edge direction histogram
        float[] edgeDirections = new float[8];
        float[] edgeMagnitudes = new float[8];
        
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                // Sobel edge detection
                int gx = getGrayValue(image, x + 1, y) - getGrayValue(image, x - 1, y);
                int gy = getGrayValue(image, x, y + 1) - getGrayValue(image, x, y - 1);
                
                double magnitude = Math.sqrt(gx * gx + gy * gy);
                double direction = Math.atan2(gy, gx);
                
                if (magnitude > 30) { // Edge threshold
                    int dirIndex = (int) ((direction + Math.PI) / (2 * Math.PI) * 8) % 8;
                    edgeDirections[dirIndex]++;
                    
                    int magIndex = (int) (magnitude / 50) % 8;
                    edgeMagnitudes[magIndex]++;
                }
            }
        }
        
        // Normalize
        int totalPixels = (width - 2) * (height - 2);
        for (int i = 0; i < 8; i++) {
            features[i] = edgeDirections[i] / totalPixels;
            features[i + 8] = edgeMagnitudes[i] / totalPixels;
        }
        
        return features;
    }

    /**
     * Get gray value at position
     */
    private int getGrayValue(BufferedImage image, int x, int y) {
        if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight()) {
            return 0;
        }
        Color color = new Color(image.getRGB(x, y));
        return (color.getRed() + color.getGreen() + color.getBlue()) / 3;
    }

    /**
     * Get neighbor gray value for LBP
     */
    private int getNeighborGray(BufferedImage image, int x, int y, int neighbor) {
        int[][] offsets = {{-1, -1}, {-1, 0}, {-1, 1}, {0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}};
        int nx = x + offsets[neighbor][0];
        int ny = y + offsets[neighbor][1];
        return getGrayValue(image, nx, ny);
    }

    /**
     * Calculate local variance
     */
    private double calculateLocalVariance(BufferedImage image, int centerX, int centerY, int windowSize) {
        double sum = 0, sumSquared = 0;
        int count = 0;
        
        for (int y = centerY - windowSize/2; y <= centerY + windowSize/2; y++) {
            for (int x = centerX - windowSize/2; x <= centerX + windowSize/2; x++) {
                int pixel = getGrayValue(image, x, y);
                sum += pixel;
                sumSquared += pixel * pixel;
                count++;
            }
        }
        
        if (count == 0) return 0;
        
        double mean = sum / count;
        double variance = (sumSquared / count) - (mean * mean);
        return Math.max(0, variance);
    }

    /**
     * Resize image
     */
    private BufferedImage resizeImage(BufferedImage image, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(image, 0, 0, width, height, null);
        g2d.dispose();
        return resized;
    }

    /**
     * Classify against design categories
     */
    private Map<String, Double> classifyAgainstCategories(float[] embedding) {
        Map<String, Double> scores = new HashMap<>();
        
        // Mock classification based on embedding features
        // In real implementation, this would use CLIP text-image similarity
        
        Random rand = new Random();
        
        // Color-based classification with more variation
        double colorScore = calculateColorScore(embedding);
        scores.put("logo", colorScore * (0.6 + rand.nextDouble() * 0.4));
        scores.put("graphic design", colorScore * (0.5 + rand.nextDouble() * 0.5));
        scores.put("illustration", colorScore * (0.4 + rand.nextDouble() * 0.6));
        
        // Texture-based classification with variation
        double textureScore = calculateTextureScore(embedding);
        scores.put("pattern", textureScore * (0.7 + rand.nextDouble() * 0.3));
        scores.put("decoration", textureScore * 0.8);
        
        // Edge-based classification
        double edgeScore = calculateEdgeScore(embedding);
        scores.put("text", edgeScore * 0.9);
        scores.put("symbol", edgeScore * 0.8);
        scores.put("icon", edgeScore * 0.7);
        
        // Default scores
        for (String category : DESIGN_CATEGORIES) {
            scores.putIfAbsent(category, 0.1);
        }
        
        return scores;
    }

    /**
     * Calculate color score
     */
    private double calculateColorScore(float[] embedding) {
        double score = 0;
        for (int i = 0; i < Math.min(64, embedding.length); i++) {
            score += Math.abs(embedding[i]);
        }
        return Math.min(1.0, score / 10.0);
    }

    /**
     * Calculate texture score
     */
    private double calculateTextureScore(float[] embedding) {
        double score = 0;
        for (int i = 64; i < Math.min(96, embedding.length); i++) {
            score += Math.abs(embedding[i]);
        }
        return Math.min(1.0, score / 5.0);
    }

    /**
     * Calculate edge score
     */
    private double calculateEdgeScore(float[] embedding) {
        double score = 0;
        for (int i = 96; i < Math.min(112, embedding.length); i++) {
            score += Math.abs(embedding[i]);
        }
        return Math.min(1.0, score / 3.0);
    }

    /**
     * Find best category
     */
    private String findBestCategory(Map<String, Double> scores) {
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
    }

    /**
     * Normalize embedding
     */
    private void normalizeEmbedding(float[] embedding) {
        double norm = 0;
        for (float value : embedding) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        
        if (norm > 0) {
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] = (float) (embedding[i] / norm);
            }
        }
    }

    /**
     * Calculate cosine similarity
     */
    private double calculateCosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1.length != embedding2.length) {
            return 0.0;
        }
        
        double dotProduct = 0;
        double norm1 = 0;
        double norm2 = 0;
        
        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }
        
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
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

    /**
     * Design classification result
     */
    public static class DesignClassification {
        private final DesignRegion region;
        private final String category;
        private final double confidence;
        private final float[] embedding;

        public DesignClassification(DesignRegion region, String category, double confidence, float[] embedding) {
            this.region = region;
            this.category = category;
            this.confidence = confidence;
            this.embedding = embedding;
        }

        public DesignRegion getRegion() { return region; }
        public String getCategory() { return category; }
        public double getConfidence() { return confidence; }
        public float[] getEmbedding() { return embedding; }
    }

    /**
     * Design embedding result
     */
    public static class DesignEmbedding {
        private final DesignRegion region;
        private final float[] embedding;

        public DesignEmbedding(DesignRegion region, float[] embedding) {
            this.region = region;
            this.embedding = embedding;
        }

        public DesignRegion getRegion() { return region; }
        public float[] getEmbedding() { return embedding; }
    }

    /**
     * Similar design result
     */
    public static class SimilarDesign {
        private final DesignRegion region;
        private final double similarity;

        public SimilarDesign(DesignRegion region, double similarity) {
            this.region = region;
            this.similarity = similarity;
        }

        public DesignRegion getRegion() { return region; }
        public double getSimilarity() { return similarity; }
    }
}
