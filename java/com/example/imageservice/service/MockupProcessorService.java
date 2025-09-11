package com.example.imageservice.service;

import com.example.imageservice.entity.MockupJob;
import com.example.imageservice.dto.CommandResult;
import com.example.imageservice.service.CommandRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockupProcessorService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final CommandRunner commandRunner;
    
    @Value("${app.mockup.temp-dir:${java.io.tmpdir}/mockup}")
    private String tempDir;
    
    @Value("${app.mockup.output-dir:${java.io.tmpdir}/mockup-output}")
    private String outputDir;
    
    @Value("${app.mockup.cache-ttl:3600}")
    private long cacheTtl;
    
    private static final String CACHE_KEY_PREFIX = "mockup:";
    
    /**
     * Xử lý tạo mockup với ImageMagick
     */
    public String processMockup(MockupJob mockupJob) throws IOException, InterruptedException {
        // Kiểm tra cache trước
        String cacheKey = generateCacheKey(mockupJob);
        String cachedResult = getFromCache(cacheKey);
        if (cachedResult != null) {
            log.info("Using cached result for mockup job: {}", mockupJob.getJobId());
            return cachedResult;
        }
        
        // Tạo thư mục temp nếu chưa có
        createDirectoriesIfNotExist();
        
        // Download ảnh từ S3 hoặc URL
        String backgroundPath = downloadImage(mockupJob.getBackgroundUrl(), "background");
        String designPath = downloadImage(mockupJob.getDesignUrl(), "design");
        
        // Tạo tên file output
        String outputFileName = generateOutputFileName(mockupJob);
        String outputPathStr = Paths.get(outputDir, outputFileName).toString();
        
        // Xử lý với ImageMagick
        processWithImageMagick(backgroundPath, designPath, outputPathStr, mockupJob);
        
        // Upload kết quả lên S3
        String s3Url = uploadResultToS3(outputPathStr, outputFileName);
        
        // Cache kết quả
        cacheResult(cacheKey, s3Url);
        
        // Cleanup temp files
        cleanupTempFiles(backgroundPath, designPath, outputPathStr);
        
        return s3Url;
    }
    
    /**
     * Xử lý với ImageMagick sử dụng ProcessBuilder
     */
    private void processWithImageMagick(String backgroundPath, String designPath, 
                                      String outputPath, MockupJob mockupJob) 
            throws IOException, InterruptedException {
        
        // Xây dựng lệnh ImageMagick
        String[] command = buildImageMagickCommand(backgroundPath, designPath, outputPath, mockupJob);
        
        log.info("Executing ImageMagick command: {}", String.join(" ", command));
        
        // Sử dụng CommandRunner để chạy lệnh
        CommandResult result = commandRunner.runImageMagickCommand(command);
        
        if (!result.isSuccess()) {
            log.error("ImageMagick failed: {}", result.getErrorMessage());
            throw new RuntimeException("ImageMagick failed: " + result.getErrorMessage());
        }
        
        log.info("ImageMagick processing completed successfully");
    }
    
    /**
     * Xây dựng lệnh ImageMagick với các option nâng cao
     */
    private String[] buildImageMagickCommand(String backgroundPath, String designPath, 
                                           String outputPath, MockupJob mockupJob) {
        
        // Sử dụng convert thay vì composite để có nhiều option hơn
        StringBuilder commandBuilder = new StringBuilder();
        commandBuilder.append("convert ");
        
        // Background image
        commandBuilder.append("\"").append(backgroundPath).append("\" ");
        
        // Design image với các option
        commandBuilder.append("\"").append(designPath).append("\" ");
        
        // Scale design nếu cần
        if (mockupJob.getDesignScale() != null && mockupJob.getDesignScale() != 1.0) {
            commandBuilder.append("-resize ").append((int)(mockupJob.getDesignScale() * 100)).append("% ");
        }
        
        // Rotation nếu cần
        if (mockupJob.getDesignRotation() != null && mockupJob.getDesignRotation() != 0.0) {
            commandBuilder.append("-rotate ").append(mockupJob.getDesignRotation()).append(" ");
        }
        
        // Opacity (transparency) - mặc định 100% (không trong suốt)
        // TODO: Thêm field opacity vào MockupJob entity
        // if (mockupJob.getOpacity() != null && mockupJob.getOpacity() < 100) {
        //     commandBuilder.append("-alpha set -channel A -evaluate set ").append(mockupJob.getOpacity()).append("% ");
        // }
        
        // Gravity và geometry cho positioning
        commandBuilder.append("-gravity center ");
        
        if (mockupJob.getDesignX() != null && mockupJob.getDesignY() != null) {
            commandBuilder.append("-geometry +").append(mockupJob.getDesignX())
                         .append("+").append(mockupJob.getDesignY()).append(" ");
        } else {
            commandBuilder.append("-geometry +0+0 ");
        }
        
        // Composite operation
        commandBuilder.append("-compose over -composite ");
        
        // Output format và quality
        if (mockupJob.getOutputFormat() != null && !mockupJob.getOutputFormat().equalsIgnoreCase("JPEG")) {
            commandBuilder.append("-format ").append(mockupJob.getOutputFormat().toLowerCase()).append(" ");
        }
        
        if (mockupJob.getOutputQuality() != null && mockupJob.getOutputQuality() != 90) {
            commandBuilder.append("-quality ").append(mockupJob.getOutputQuality()).append(" ");
        }
        
        // Output file
        commandBuilder.append("\"").append(outputPath).append("\"");
        
        return commandBuilder.toString().split("\\s+");
    }
    
    /**
     * Tạo tên file output
     */
    private String generateOutputFileName(MockupJob mockupJob) {
        String format = mockupJob.getOutputFormat() != null ? 
                mockupJob.getOutputFormat().toLowerCase() : "jpg";
        return String.format("mockup_%s.%s", mockupJob.getJobId(), format);
    }
    
    /**
     * Tạo cache key
     */
    private String generateCacheKey(MockupJob mockupJob) {
        return CACHE_KEY_PREFIX + String.format("%s_%s_%s_%s_%s_%s_%s_%s",
                mockupJob.getBackgroundUrl().hashCode(),
                mockupJob.getDesignUrl().hashCode(),
                mockupJob.getDesignX() != null ? mockupJob.getDesignX() : 0,
                mockupJob.getDesignY() != null ? mockupJob.getDesignY() : 0,
                mockupJob.getDesignScale() != null ? mockupJob.getDesignScale() : 1.0,
                mockupJob.getDesignRotation() != null ? mockupJob.getDesignRotation() : 0.0,
                mockupJob.getOutputFormat() != null ? mockupJob.getOutputFormat() : "JPEG",
                mockupJob.getOutputQuality() != null ? mockupJob.getOutputQuality() : 90
        );
    }
    
    /**
     * Lấy kết quả từ cache
     */
    private String getFromCache(String cacheKey) {
        try {
            return redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Failed to get from cache: {}", cacheKey, e);
            return null;
        }
    }
    
    /**
     * Cache kết quả
     */
    private void cacheResult(String cacheKey, String result) {
        try {
            redisTemplate.opsForValue().set(cacheKey, result, cacheTtl, TimeUnit.SECONDS);
            log.debug("Cached mockup result: {}", cacheKey);
        } catch (Exception e) {
            log.warn("Failed to cache result: {}", cacheKey, e);
        }
    }
    
    /**
     * Tạo thư mục nếu chưa có
     */
    private void createDirectoriesIfNotExist() throws IOException {
        Files.createDirectories(Paths.get(tempDir));
        Files.createDirectories(Paths.get(outputDir));
    }
    
    /**
     * Download ảnh từ URL hoặc S3
     */
    private String downloadImage(String imageUrl, String prefix) throws IOException {
        // TODO: Implement actual download logic
        // Tạm thời tạo file giả để test
        String fileName = prefix + "_" + System.currentTimeMillis() + ".jpg";
        String filePath = Paths.get(tempDir, fileName).toString();
        
        // Tạo file trống để test
        Files.createFile(Paths.get(filePath));
        
        log.debug("Downloaded image to: {}", filePath);
        return filePath;
    }
    
    /**
     * Upload kết quả lên S3
     */
    private String uploadResultToS3(String filePath, String fileName) {
        // TODO: Implement actual S3 upload logic
        // Tạm thời return URL giả
        String s3Url = "https://s3.amazonaws.com/mockup-bucket/" + fileName;
        log.debug("Uploaded result to S3: {}", s3Url);
        return s3Url;
    }
    
    /**
     * Cleanup temporary files
     */
    private void cleanupTempFiles(String... filePaths) {
        for (String filePath : filePaths) {
            try {
                Files.deleteIfExists(Paths.get(filePath));
                log.debug("Cleaned up temp file: {}", filePath);
            } catch (IOException e) {
                log.warn("Failed to cleanup temp file: {}", filePath, e);
            }
        }
    }
}
