package com.example.imageservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// ONNX Runtime imports
import ai.onnxruntime.*;

/**
 * Service for AI-powered design detection using YOLOv8
 * Detects multiple design regions in mockup images
 */
@Service
@Slf4j
public class DesignDetectionService {

    @Value("${ai.yolov8.model-path:models/yolov8n.onnx}")
    private String modelPath;
    
    @Value("${ai.yolov8.confidence-threshold:0.5}")
    private double confidenceThreshold;
    
    @Value("${ai.yolov8.nms-threshold:0.4}")
    private double nmsThreshold;
    
    @Value("${ai.yolov8.input-size:640}")
    private int inputSize;

    // Model cache để tránh reload
    private final Map<String, Object> modelCache = new ConcurrentHashMap<>();
    
    // ONNX Runtime session và environment
    private OrtEnvironment ortEnvironment;
    private OrtSession ortSession;
    private boolean modelReady = false;
    
    // Generic Design Detection Service
    @Autowired
    private GenericDesignDetectionService genericDetectionService;
    
    // CLIP Design Detection Service
    @Autowired
    private CLIPDesignDetectionService clipDetectionService;

    @PostConstruct
    public void initializeModel() {
        try {
            log.info("Initializing YOLOv8 model for design detection...");
            log.info("Model path: {}", modelPath);
            log.info("Confidence threshold: {}", confidenceThreshold);
            log.info("NMS threshold: {}", nmsThreshold);
            log.info("Input size: {}", inputSize);
            
            // Initialize ONNX Runtime environment
            ortEnvironment = OrtEnvironment.getEnvironment();
            log.info("ONNX Runtime environment initialized");
            
            // Check if model file exists
            Path modelFilePath = Paths.get(modelPath);
            log.info("Checking model file at: {}", modelFilePath.toAbsolutePath());
            if (!Files.exists(modelFilePath)) {
                log.warn("Model file not found at: {}. Using mock mode.", modelPath);
                modelReady = false;
                return;
            }
            
            log.info("Model file exists, size: {} bytes", Files.size(modelFilePath));
            
            // Load ONNX model
            log.info("Loading ONNX model...");
            ortSession = ortEnvironment.createSession(modelPath, new OrtSession.SessionOptions());
            log.info("YOLOv8 ONNX model loaded successfully");
            
            // Get model input info
            Map<String, NodeInfo> inputInfo = ortSession.getInputInfo();
            for (Map.Entry<String, NodeInfo> entry : inputInfo.entrySet()) {
                log.info("Model input: {} - {}", entry.getKey(), entry.getValue().getInfo());
            }
            
            modelReady = true;
            log.info("YOLOv8 model initialized successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize YOLOv8 model: {}", e.getMessage(), e);
            modelReady = false;
            // Don't throw exception, allow service to start in mock mode
        }
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (ortSession != null) {
                ortSession.close();
                log.info("YOLOv8 ONNX session closed");
            }
            if (ortEnvironment != null) {
                ortEnvironment.close();
                log.info("ONNX Runtime environment closed");
            }
        } catch (Exception e) {
            log.error("Error closing YOLOv8 model session", e);
        }
    }

    /**
     * Check if model is ready for inference
     * @return true if model is loaded and ready
     */
    public boolean isModelReady() {
        return modelReady && ortSession != null;
    }

    /**
     * Get model information
     * @return Map containing model info
     */
    public Map<String, Object> getModelInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("modelPath", modelPath);
        info.put("confidenceThreshold", confidenceThreshold);
        info.put("nmsThreshold", nmsThreshold);
        info.put("inputSize", inputSize);
        info.put("isReady", isModelReady());
        return info;
    }

    /**
     * Detect design regions in mockup image
     * @param imageFile Uploaded image file
     * @return List of detected design regions
     */
    public List<DesignRegion> detectDesigns(MultipartFile imageFile) {
        try {
            log.info("Starting hybrid design detection for image: {}", imageFile.getOriginalFilename());
            
            // Convert MultipartFile to BufferedImage
            BufferedImage image = convertToBufferedImage(imageFile);
            
            List<DesignRegion> allRegions = new ArrayList<>();
            
            // 1. YOLOv8 detection (for known objects)
            if (isModelReady()) {
                try {
                    log.info("Running YOLOv8 detection...");
                    BufferedImage preprocessedImage = preprocessImage(image);
                    List<DetectionResult> yoloDetections = runInference(preprocessedImage);
                    List<DesignRegion> yoloRegions = postProcessDetections(yoloDetections, image);
                    allRegions.addAll(yoloRegions);
                    log.info("YOLOv8 detected {} regions", yoloRegions.size());
                } catch (Exception e) {
                    log.warn("YOLOv8 detection failed, continuing with generic detection: {}", e.getMessage());
                }
            }
            
            // 2. Generic detection (for unknown designs)
            try {
                log.info("Running generic design detection...");
                List<GenericDesignDetectionService.DesignRegion> genericRegions = 
                    genericDetectionService.detectDesigns(image);
                
                // Convert to DesignRegion format
                for (GenericDesignDetectionService.DesignRegion genericRegion : genericRegions) {
                    allRegions.add(new DesignRegion(
                        genericRegion.getX(), genericRegion.getY(),
                        genericRegion.getWidth(), genericRegion.getHeight(),
                        (float) genericRegion.getConfidence(), genericRegion.getLabel()
                    ));
                }
                log.info("Generic detection found {} regions", genericRegions.size());
            } catch (Exception e) {
                log.error("Generic detection failed: {}", e.getMessage(), e);
                // Don't fail completely, continue with YOLOv8 results
            }
            
            // 3. CLIP classification and enhancement
            List<DesignRegion> enhancedRegions = new ArrayList<>();
            if (!allRegions.isEmpty()) {
                try {
                    log.info("Running CLIP classification...");
                    List<CLIPDesignDetectionService.DesignClassification> classifications = 
                        clipDetectionService.classifyDesignRegions(image, convertToCLIPRegions(allRegions));
                    
                    // Convert back to DesignRegion with enhanced labels
                    for (CLIPDesignDetectionService.DesignClassification classification : classifications) {
                        enhancedRegions.add(new DesignRegion(
                            classification.getRegion().getX(),
                            classification.getRegion().getY(),
                            classification.getRegion().getWidth(),
                            classification.getRegion().getHeight(),
                            (float) classification.getConfidence(),
                            classification.getCategory()
                        ));
                    }
                    log.info("CLIP classification completed for {} regions", enhancedRegions.size());
                } catch (Exception e) {
                    log.warn("CLIP classification failed, using original regions: {}", e.getMessage());
                    enhancedRegions = allRegions;
                }
            }
            
            // 4. Merge and filter results
            List<DesignRegion> finalRegions = mergeAndFilterResults(enhancedRegions);
            
            log.info("Hybrid design detection completed. Found {} total design regions", finalRegions.size());
            return finalRegions;
            
        } catch (Exception e) {
            log.error("Error during design detection", e);
            throw new RuntimeException("Design detection failed", e);
        }
    }

    /**
     * Detect design regions from image URL
     * @param imageUrl URL of the image
     * @return List of detected design regions
     */
    public List<DesignRegion> detectDesignsFromUrl(String imageUrl) {
        try {
            log.info("Starting design detection for image URL: {}", imageUrl);
            
            // TODO: Download image from URL
            // BufferedImage image = downloadImageFromUrl(imageUrl);
            
            // For now, return empty list
            log.warn("URL-based detection not implemented yet");
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("Error during URL-based design detection", e);
            throw new RuntimeException("URL-based design detection failed", e);
        }
    }

    /**
     * Convert MultipartFile to BufferedImage
     */
    private BufferedImage convertToBufferedImage(MultipartFile file) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(file.getBytes())) {
            return javax.imageio.ImageIO.read(bis);
        }
    }

    /**
     * Preprocess image for YOLOv8 input
     */
    private BufferedImage preprocessImage(BufferedImage image) {
        log.debug("Preprocessing image: {}x{}", image.getWidth(), image.getHeight());
        
        // Resize image to model input size (640x640)
        BufferedImage resized = new BufferedImage(inputSize, inputSize, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(image, 0, 0, inputSize, inputSize, null);
        g2d.dispose();
        
        return resized;
    }

    /**
     * Run YOLOv8 inference
     */
    private List<DetectionResult> runInference(BufferedImage image) {
        log.debug("Running YOLOv8 inference on preprocessed image");
        
        if (!isModelReady()) {
            log.warn("Model not ready, returning mock results");
            return getMockDetectionResults();
        }
        
        try {
            // Convert BufferedImage to float array for ONNX input
            float[][][][] inputArray = convertImageToFloatArray(image);
            
            // Create ONNX tensor
            OnnxTensor inputTensor = OnnxTensor.createTensor(ortEnvironment, inputArray);
            
            // Run inference
            Map<String, OnnxTensor> inputs = Map.of("images", inputTensor);
            try (OrtSession.Result result = ortSession.run(inputs)) {
                // Get output tensor
                float[][][] output = (float[][][]) result.get(0).getValue();
                
                // Parse YOLOv8 output
                return parseYOLOv8Output(output, image.getWidth(), image.getHeight());
            }
            
        } catch (Exception e) {
            log.error("Error during YOLOv8 inference: {}", e.getMessage(), e);
            return getMockDetectionResults();
        }
    }

    /**
     * Post-process detection results
     */
    private List<DesignRegion> postProcessDetections(List<DetectionResult> detections, BufferedImage originalImage) {
        List<DesignRegion> regions = new ArrayList<>();
        
        for (DetectionResult detection : detections) {
            if (detection.confidence >= confidenceThreshold) {
                DesignRegion region = new DesignRegion(
                    detection.x,
                    detection.y,
                    detection.width,
                    detection.height,
                    detection.confidence,
                    detection.className
                );
                regions.add(region);
            }
        }
        
        // TODO: Apply Non-Maximum Suppression (NMS)
        // regions = applyNMS(regions, nmsThreshold);
        
        return regions;
    }


    /**
     * Convert BufferedImage to float array for ONNX input
     */
    private float[][][][] convertImageToFloatArray(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        float[][][][] inputArray = new float[1][3][height][width];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                // Normalize to [0, 1] and convert to float
                inputArray[0][0][y][x] = r / 255.0f; // Red channel
                inputArray[0][1][y][x] = g / 255.0f; // Green channel
                inputArray[0][2][y][x] = b / 255.0f; // Blue channel
            }
        }
        
        return inputArray;
    }

    /**
     * Parse YOLOv8 output to detection results
     */
    private List<DetectionResult> parseYOLOv8Output(float[][][] output, int imageWidth, int imageHeight) {
        List<DetectionResult> detections = new ArrayList<>();
        
        // YOLOv8 output format: [1, 84, 8400]
        // 84 = 4 (bbox) + 80 (COCO classes)
        int numDetections = output[0][0].length; // 8400
        int numClasses = 80; // COCO classes
        
        for (int i = 0; i < numDetections; i++) {
            // Get confidence scores for all classes
            float maxConfidence = 0;
            int bestClass = -1;
            
            for (int c = 0; c < numClasses; c++) {
                float confidence = output[0][4 + c][i];
                if (confidence > maxConfidence) {
                    maxConfidence = confidence;
                    bestClass = c;
                }
            }
            
            // Filter by confidence threshold
            if (maxConfidence > confidenceThreshold) {
                // Get bounding box coordinates
                float centerX = output[0][0][i];
                float centerY = output[0][1][i];
                float width = output[0][2][i];
                float height = output[0][3][i];
                
                // Convert to pixel coordinates (YOLOv8 outputs are normalized to [0,1])
                // Scale to original image size, not inputSize
                int x = (int) ((centerX - width / 2) * imageWidth);
                int y = (int) ((centerY - height / 2) * imageHeight);
                int w = (int) (width * imageWidth);
                int h = (int) (height * imageHeight);
                
                // Validate coordinates
                if (x < 0 || y < 0 || w <= 0 || h <= 0 || 
                    x >= imageWidth || y >= imageHeight ||
                    x + w > imageWidth || y + h > imageHeight) {
                    log.warn("Invalid YOLOv8 coordinates: centerX={}, centerY={}, width={}, height={}, " +
                        "scaled: x={}, y={}, w={}, h={}, imageSize={}x{}", 
                        centerX, centerY, width, height, x, y, w, h, imageWidth, imageHeight);
                    continue; // Skip invalid detection
                }
                
                detections.add(new DetectionResult(maxConfidence, x, y, w, h, "design"));
            }
        }
        
        return detections;
    }

    /**
     * Get mock detection results for testing
     */
    private List<DetectionResult> getMockDetectionResults() {
        List<DetectionResult> mockResults = new ArrayList<>();
        mockResults.add(new DetectionResult(0.95f, 50, 50, 100, 100, "logo"));
        mockResults.add(new DetectionResult(0.88f, 200, 150, 80, 80, "text"));
        return mockResults;
    }

    /**
     * Merge and filter results from different detection methods
     */
    private List<DesignRegion> mergeAndFilterResults(List<DesignRegion> allRegions) {
        if (allRegions.isEmpty()) {
            return allRegions;
        }
        
        // Remove overlapping regions
        List<DesignRegion> filtered = new ArrayList<>();
        
        for (DesignRegion region : allRegions) {
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
        
        // Sort by confidence (highest first)
        filtered.sort((r1, r2) -> Double.compare(r2.getConfidence(), r1.getConfidence()));
        
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
     * Convert DesignRegion to CLIPDesignDetectionService.DesignRegion
     */
    private List<CLIPDesignDetectionService.DesignRegion> convertToCLIPRegions(List<DesignRegion> regions) {
        List<CLIPDesignDetectionService.DesignRegion> clipRegions = new ArrayList<>();
        
        for (DesignRegion region : regions) {
            clipRegions.add(new CLIPDesignDetectionService.DesignRegion(
                region.getX(), region.getY(), region.getWidth(), region.getHeight(),
                region.getClassName(), region.getConfidence()
            ));
        }
        
        return clipRegions;
    }

    /**
     * Design region data class
     */
    public static class DesignRegion {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final double confidence;
        private final String className;

        public DesignRegion(int x, int y, int width, int height, double confidence, String className) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.confidence = confidence;
            this.className = className;
        }

        // Getters
        public int getX() { return x; }
        public int getY() { return y; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public double getConfidence() { return confidence; }
        public String getClassName() { return className; }

        @Override
        public String toString() {
            return String.format("DesignRegion{x=%d, y=%d, w=%d, h=%d, conf=%.2f, class='%s'}", 
                x, y, width, height, confidence, className);
        }
    }

    /**
     * Detection result data class
     */
    private static class DetectionResult {
        private final double confidence;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final String className;

        public DetectionResult(double confidence, int x, int y, int width, int height, String className) {
            this.confidence = confidence;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.className = className;
        }
    }
}
