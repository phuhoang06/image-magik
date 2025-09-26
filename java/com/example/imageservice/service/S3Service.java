package com.example.imageservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

/**
 * Service for S3 operations
 */
@Service
@Slf4j
public class S3Service {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.s3.bucket:images-service-bucket}")
    private String bucketName;

    private volatile S3Client s3Client;

    private S3Client getS3Client() {
        if (s3Client == null) {
            synchronized (this) {
                if (s3Client == null) {
                    s3Client = S3Client.builder()
                            .region(Region.of(awsRegion))
                            .credentialsProvider(DefaultCredentialsProvider.create())
                            .build();
                }
            }
        }
        return s3Client;
    }

    /**
     * Upload file to S3
     */
    public String uploadFile(InputStream inputStream, String key, String contentType) {
        return uploadFile(inputStream, key, contentType, -1);
    }
    
    /**
     * Upload file to S3 with content length
     */
    public String uploadFile(InputStream inputStream, String key, String contentType, long contentLength) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .cacheControl("public, max-age=31536000")
                    .build();

            RequestBody requestBody;
            if (contentLength > 0) {
                requestBody = RequestBody.fromInputStream(inputStream, contentLength);
            } else {
                // For unknown length, read all bytes first
                byte[] bytes = inputStream.readAllBytes();
                requestBody = RequestBody.fromBytes(bytes);
            }
            
            getS3Client().putObject(request, requestBody);
            
            log.info("Uploaded file to S3: s3://{}/{}", bucketName, key);
            return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, awsRegion, key);
            
        } catch (Exception e) {
            log.error("Failed to upload file to S3: {}", key, e);
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    /**
     * Upload file with metadata
     */
    public String uploadFileWithMetadata(InputStream inputStream, String key, String contentType, 
                                       java.util.Map<String, String> metadata) {
        try {
            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .cacheControl("public, max-age=31536000");

            if (metadata != null && !metadata.isEmpty()) {
                requestBuilder.metadata(metadata);
            }

            getS3Client().putObject(requestBuilder.build(), 
                    RequestBody.fromInputStream(inputStream, -1));
            
            log.info("Uploaded file with metadata to S3: s3://{}/{}", bucketName, key);
            return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, awsRegion, key);
            
        } catch (Exception e) {
            log.error("Failed to upload file with metadata to S3: {}", key, e);
            throw new RuntimeException("Failed to upload file with metadata to S3", e);
        }
    }

    /**
     * Generate S3 key for design extraction
     */
    public String generateDesignKey(String jobId, String designId, String extension) {
        return String.format("design-extraction/%s/%s.%s", jobId, designId, extension);
    }

    /**
     * Generate S3 key for mockup
     */
    public String generateMockupKey(String jobId, String extension) {
        return String.format("design-extraction/%s/mockup.%s", jobId, extension);
    }
}
