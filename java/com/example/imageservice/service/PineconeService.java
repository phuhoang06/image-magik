package com.example.imageservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Pinecone Vector Database Service
 * Uses HTTP client to interact with Pinecone REST API
 */
@Service
@Slf4j
public class PineconeService {

    @Value("${pinecone.api-key:}")
    private String pineconeApiKey;
    
    @Value("${pinecone.environment:us-west1-gcp}")
    private String pineconeEnvironment;
    
    @Value("${pinecone.index-name:design-similarity-index}")
    private String indexName;
    
    @Value("${pinecone.dimension:512}")
    private int vectorDimension;

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PineconeService() {
        this.restTemplate = new RestTemplate();
        this.baseUrl = "https://controller." + pineconeEnvironment + ".pinecone.io";
    }

    /**
     * Initialize Pinecone connection and create index if not exists
     */
    public void initialize() {
        if (pineconeApiKey == null || pineconeApiKey.isEmpty()) {
            log.warn("Pinecone API key not configured. Using mock implementation.");
            return;
        }
        
        try {
            log.info("Initializing Pinecone connection...");
            
            // Check if index exists
            if (!indexExists()) {
                createIndex();
            }
            
            log.info("Pinecone initialized successfully with index: {}", indexName);
        } catch (Exception e) {
            log.error("Failed to initialize Pinecone: {}", e.getMessage(), e);
        }
    }

    /**
     * Check if index exists
     */
    public boolean indexExists() {
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/databases",
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return response.getBody() != null && response.getBody().contains(indexName);
        } catch (Exception e) {
            log.error("Error checking if index exists: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Create Pinecone index
     */
    public void createIndex() {
        try {
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("name", indexName);
            requestBody.put("dimension", vectorDimension);
            requestBody.put("metric", "cosine");
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            restTemplate.postForEntity(
                baseUrl + "/databases",
                entity,
                String.class
            );
            
            log.info("Created Pinecone index: {}", indexName);
        } catch (Exception e) {
            log.error("Error creating index: {}", e.getMessage(), e);
        }
    }

    /**
     * Upsert vectors to Pinecone index
     */
    public void upsertVectors(List<VectorData> vectors) {
        if (pineconeApiKey == null || pineconeApiKey.isEmpty()) {
            log.debug("Pinecone not configured, skipping vector upsert");
            return;
        }
        
        try {
            String indexUrl = "https://" + indexName + "-" + pineconeEnvironment + ".svc.pinecone.io";
            
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("vectors", vectors);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            restTemplate.postForEntity(
                indexUrl + "/vectors/upsert",
                entity,
                String.class
            );
            
            log.info("Upserted {} vectors to Pinecone", vectors.size());
        } catch (Exception e) {
            log.error("Error upserting vectors: {}", e.getMessage(), e);
        }
    }

    /**
     * Query similar vectors from Pinecone
     */
    public List<QueryResult> querySimilarVectors(float[] queryVector, int topK) {
        if (pineconeApiKey == null || pineconeApiKey.isEmpty()) {
            log.debug("Pinecone not configured, returning mock results");
            return generateMockQueryResults(topK);
        }
        
        try {
            String indexUrl = "https://" + indexName + "-" + pineconeEnvironment + ".svc.pinecone.io";
            
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("vector", queryVector);
            requestBody.put("topK", topK);
            requestBody.put("includeMetadata", true);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                indexUrl + "/query",
                entity,
                Map.class
            );
            
            return parseQueryResponse(response.getBody());
        } catch (Exception e) {
            log.error("Error querying vectors: {}", e.getMessage(), e);
            return generateMockQueryResults(topK);
        }
    }

    /**
     * Create HTTP headers with API key
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Api-Key", pineconeApiKey);
        return headers;
    }

    /**
     * Parse query response from Pinecone
     */
    private List<QueryResult> parseQueryResponse(Map<String, Object> response) {
        List<QueryResult> results = new ArrayList<>();
        
        if (response != null && response.containsKey("matches")) {
            List<Map<String, Object>> matches = (List<Map<String, Object>>) response.get("matches");
            
            for (Map<String, Object> match : matches) {
                String id = (String) match.get("id");
                Double score = (Double) match.get("score");
                Map<String, Object> metadata = (Map<String, Object>) match.get("metadata");
                
                results.add(new QueryResult(id, score.floatValue(), metadata));
            }
        }
        
        return results;
    }

    /**
     * Generate mock query results for testing
     */
    private List<QueryResult> generateMockQueryResults(int topK) {
        List<QueryResult> results = new ArrayList<>();
        Random rand = new Random();
        
        for (int i = 0; i < Math.min(topK, 5); i++) {
            String id = "mock_design_" + (i + 1);
            float score = 0.5f + rand.nextFloat() * 0.5f; // 0.5 to 1.0
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("category", "mock_category_" + (i % 3));
            metadata.put("confidence", score);
            
            results.add(new QueryResult(id, score, metadata));
        }
        
        return results;
    }

    /**
     * Data class for vector data
     */
    public static class VectorData {
        private String id;
        private float[] values;
        private Map<String, Object> metadata;

        public VectorData(String id, float[] values, Map<String, Object> metadata) {
            this.id = id;
            this.values = values;
            this.metadata = metadata;
        }

        public String getId() { return id; }
        public float[] getValues() { return values; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    /**
     * Data class for query results
     */
    public static class QueryResult {
        private String id;
        private float score;
        private Map<String, Object> metadata;

        public QueryResult(String id, float score, Map<String, Object> metadata) {
            this.id = id;
            this.score = score;
            this.metadata = metadata;
        }

        public String getId() { return id; }
        public float getScore() { return score; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
}
