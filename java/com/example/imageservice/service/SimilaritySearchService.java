package com.example.imageservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

/**
 * Service for finding similar designs using CLIP embeddings
 * Provides similarity search functionality for design comparison
 */
@Service
@Slf4j
public class SimilaritySearchService {

    @Autowired
    private CLIPDesignDetectionService clipService;
    
    @Autowired
    private PineconeService pineconeService;

    /**
     * Find similar designs for a given design image
     * @param designImage The design image to find similarities for
     * @param topK Number of similar designs to return
     * @return List of similar designs with similarity scores
     */
    public List<SimilarDesign> findSimilarDesigns(BufferedImage designImage, int topK) {
        try {
            log.info("Finding {} similar designs for input image", topK);
            
            // Generate embedding for input design (mock for now)
            float[] queryEmbedding = generateMockEmbedding();
            
            // Query Pinecone vector database for similar designs
            List<PineconeService.QueryResult> pineconeResults = pineconeService.querySimilarVectors(queryEmbedding, topK);
            
            // Convert Pinecone results to SimilarDesign objects
            List<SimilarDesign> similarDesigns = new ArrayList<>();
            for (PineconeService.QueryResult result : pineconeResults) {
                String category = "unknown";
                if (result.getMetadata() != null && result.getMetadata().containsKey("category")) {
                    category = (String) result.getMetadata().get("category");
                }
                
                similarDesigns.add(new SimilarDesign(
                    result.getId(),
                    category,
                    result.getScore(),
                    0, 0, 100, 100,  // Mock coordinates
                    new byte[0],     // Mock image data
                    "image/png"      // Mock content type
                ));
            }
            
            log.info("Found {} similar designs from Pinecone", similarDesigns.size());
            return similarDesigns;
            
        } catch (Exception e) {
            log.error("Error finding similar designs", e);
            throw new RuntimeException("Similarity search failed", e);
        }
    }

    /**
     * Compare two design images for similarity
     * @param image1 First design image
     * @param image2 Second design image
     * @return Similarity score between 0 and 1
     */
    public double compareDesigns(BufferedImage image1, BufferedImage image2) {
        try {
            log.info("Comparing two design images for similarity");
            
            // Generate embeddings for both images (mock for now)
            float[] embedding1 = generateMockEmbedding();
            float[] embedding2 = generateMockEmbedding();
            
            // Calculate cosine similarity (mock for now)
            double similarity = calculateMockSimilarity(embedding1, embedding2);
            
            log.info("Design similarity score: {}", similarity);
            return similarity;
            
        } catch (Exception e) {
            log.error("Error comparing designs", e);
            throw new RuntimeException("Design comparison failed", e);
        }
    }

    /**
     * Find similar designs from a list of candidate designs
     * @param queryImage Query design image
     * @param candidateDesigns List of candidate designs to compare against
     * @param topK Number of similar designs to return
     * @return List of similar designs with similarity scores
     */
    public List<SimilarDesign> findSimilarFromCandidates(BufferedImage queryImage, 
                                                        List<DesignExtractionService.ExtractedDesign> candidateDesigns, 
                                                        int topK) {
        try {
            log.info("Finding similar designs from {} candidates", candidateDesigns.size());
            
            // Generate embedding for query image (mock for now)
            float[] queryEmbedding = generateMockEmbedding();
            
            List<SimilarDesign> similarDesigns = new ArrayList<>();
            
            for (DesignExtractionService.ExtractedDesign candidate : candidateDesigns) {
                try {
                    // Convert candidate image data to BufferedImage
                    BufferedImage candidateImage = convertToBufferedImage(candidate.getImageData());
                    
                    // Generate embedding for candidate (mock for now)
                    float[] candidateEmbedding = generateMockEmbedding();
                    
                    // Calculate similarity (mock for now)
                    double similarity = calculateMockSimilarity(queryEmbedding, candidateEmbedding);
                    
                    // Create similar design object
                    SimilarDesign similarDesign = new SimilarDesign(
                        candidate.getId(),
                        candidate.getCategory(),
                        similarity,
                        candidate.getX(),
                        candidate.getY(),
                        candidate.getWidth(),
                        candidate.getHeight(),
                        candidate.getImageData(),
                        candidate.getContentType()
                    );
                    
                    similarDesigns.add(similarDesign);
                    
                } catch (Exception e) {
                    log.warn("Failed to process candidate design {}: {}", candidate.getId(), e.getMessage());
                }
            }
            
            // Sort by similarity score (highest first) and return top K
            similarDesigns.sort((d1, d2) -> Double.compare(d2.getSimilarity(), d1.getSimilarity()));
            
            List<SimilarDesign> topResults = similarDesigns.subList(0, Math.min(topK, similarDesigns.size()));
            
            log.info("Found {} similar designs from candidates", topResults.size());
            return topResults;
            
        } catch (Exception e) {
            log.error("Error finding similar designs from candidates", e);
            throw new RuntimeException("Candidate similarity search failed", e);
        }
    }

    /**
     * Mock similarity search for testing
     */
    private List<SimilarDesign> performMockSimilaritySearch(float[] queryEmbedding, int topK) {
        List<SimilarDesign> mockResults = new ArrayList<>();
        Random rand = new Random();
        
        String[] categories = {"logo", "text", "graphic design", "illustration", "pattern"};
        String[] designIds = {"design_001", "design_002", "design_003", "design_004", "design_005"};
        
        for (int i = 0; i < topK; i++) {
            String category = categories[rand.nextInt(categories.length)];
            String designId = designIds[rand.nextInt(designIds.length)];
            double similarity = 0.7 + rand.nextDouble() * 0.3; // 0.7-1.0
            
            SimilarDesign similarDesign = new SimilarDesign(
                designId + "_" + i,
                category,
                similarity,
                rand.nextInt(100),
                rand.nextInt(100),
                rand.nextInt(200) + 50,
                rand.nextInt(200) + 50,
                new byte[0], // Mock image data
                "image/png"
            );
            
            mockResults.add(similarDesign);
        }
        
        return mockResults;
    }

    /**
     * Convert byte array to BufferedImage
     */
    private BufferedImage convertToBufferedImage(byte[] imageData) throws Exception {
        // Mock implementation - in real scenario, use ImageIO.read()
        return new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    }

    /**
     * Generate mock embedding for testing
     */
    private float[] generateMockEmbedding() {
        float[] embedding = new float[512]; // CLIP embedding size
        Random rand = new Random();
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = rand.nextFloat() * 2 - 1; // Range [-1, 1]
        }
        return embedding;
    }

    /**
     * Calculate mock similarity between embeddings
     */
    private double calculateMockSimilarity(float[] embedding1, float[] embedding2) {
        // Mock cosine similarity calculation
        Random rand = new Random();
        return 0.3 + rand.nextDouble() * 0.7; // Range [0.3, 1.0]
    }

    /**
     * Store design embeddings in Pinecone database
     * @param designId Unique identifier for the design
     * @param embedding CLIP embedding vector
     * @param metadata Additional metadata (category, confidence, etc.)
     */
    public void storeDesignEmbedding(String designId, float[] embedding, Map<String, Object> metadata) {
        try {
            log.info("Storing design embedding for ID: {}", designId);
            
            PineconeService.VectorData vectorData = new PineconeService.VectorData(
                designId,
                embedding,
                metadata
            );
            
            List<PineconeService.VectorData> vectors = List.of(vectorData);
            pineconeService.upsertVectors(vectors);
            
            log.info("Successfully stored design embedding for ID: {}", designId);
        } catch (Exception e) {
            log.error("Error storing design embedding for ID: {}", designId, e);
        }
    }

    /**
     * Store multiple design embeddings in batch
     * @param designEmbeddings List of design embeddings to store
     */
    public void storeDesignEmbeddingsBatch(List<DesignEmbedding> designEmbeddings) {
        try {
            log.info("Storing {} design embeddings in batch", designEmbeddings.size());
            
            List<PineconeService.VectorData> vectors = new ArrayList<>();
            for (DesignEmbedding embedding : designEmbeddings) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("category", embedding.getCategory());
                metadata.put("confidence", embedding.getConfidence());
                metadata.put("timestamp", System.currentTimeMillis());
                
                vectors.add(new PineconeService.VectorData(
                    embedding.getId(),
                    embedding.getEmbedding(),
                    metadata
                ));
            }
            
            pineconeService.upsertVectors(vectors);
            log.info("Successfully stored {} design embeddings in batch", designEmbeddings.size());
        } catch (Exception e) {
            log.error("Error storing design embeddings in batch", e);
        }
    }

    /**
     * Data class for design embedding
     */
    public static class DesignEmbedding {
        private final String id;
        private final String category;
        private final double confidence;
        private final float[] embedding;

        public DesignEmbedding(String id, String category, double confidence, float[] embedding) {
            this.id = id;
            this.category = category;
            this.confidence = confidence;
            this.embedding = embedding;
        }

        public String getId() { return id; }
        public String getCategory() { return category; }
        public double getConfidence() { return confidence; }
        public float[] getEmbedding() { return embedding; }
    }

    /**
     * Data class for similar design result
     */
    public static class SimilarDesign {
        private final String id;
        private final String category;
        private final double similarity;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final byte[] imageData;
        private final String contentType;

        public SimilarDesign(String id, String category, double similarity,
                           int x, int y, int width, int height,
                           byte[] imageData, String contentType) {
            this.id = id;
            this.category = category;
            this.similarity = similarity;
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
        public double getSimilarity() { return similarity; }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public byte[] getImageData() { return imageData; }
        public String getContentType() { return contentType; }
    }
}
