package com.example.imageservice.service;

import com.example.imageservice.entity.MockupJob;
import com.example.imageservice.dto.MockupRequestDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class LambdaService {

    @Value("${aws.region}")
    private String awsRegion;

    // Function name configured in application.yml: aws.lambda.upload-processor
    @Value("${aws.lambda.upload-processor}")
    private String lambdaFunctionName;

    // Secret used by worker to call back BE
    @Value("${lambda.callback.secret}")
    private String lambdaWorkerSecret;

    // Base URL for Lambda to call back BE (without path). Example: https://your-be-domain
    @Value("${lambda.callback.url}")
    private String lambdaCallbackBaseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile LambdaClient lambdaClient;

    /**
     * Call design extraction Lambda function (synchronous)
     */
    public Map<String, Object> callDesignExtractionLambda(Map<String, Object> payload) {
        try {
            payload.put("callbackUrl", lambdaCallbackBaseUrl);
            if (lambdaWorkerSecret != null && !lambdaWorkerSecret.isEmpty()) {
                payload.put("workerSecret", lambdaWorkerSecret);
            }

            String json = objectMapper.writeValueAsString(payload);
            
            InvokeRequest invokeRequest = InvokeRequest.builder()
                    .functionName(lambdaFunctionName)
                    .payload(SdkBytes.fromString(json, StandardCharsets.UTF_8))
                    .build();

            InvokeResponse response = getLambdaClient().invoke(invokeRequest);
            String responsePayload = response.payload().asString(StandardCharsets.UTF_8);
            
            log.info("Design extraction Lambda invoked successfully");
            log.info("Raw Lambda response: {}", responsePayload);
            
            // Parse the response - Lambda returns {statusCode: 200, body: "..."}
            Map<String, Object> wrapperResponse = objectMapper.readValue(responsePayload, new TypeReference<Map<String, Object>>() {});
            
            // Extract the actual response from the "body" field
            String bodyString = (String) wrapperResponse.get("body");
            if (bodyString != null) {
                return objectMapper.readValue(bodyString, new TypeReference<Map<String, Object>>() {});
            } else {
                // If no body field, return the wrapper response directly
                return wrapperResponse;
            }
            
        } catch (Exception e) {
            log.error("Error invoking design extraction Lambda", e);
            throw new RuntimeException("Failed to invoke design extraction Lambda", e);
        }
    }


    private LambdaClient getLambdaClient() {
        if (lambdaClient == null) {
            synchronized (this) {
                if (lambdaClient == null) {
                    lambdaClient = LambdaClient.builder()
                            .region(Region.of(awsRegion))
                            .build();
                }
            }
        }
        return lambdaClient;
    }

    private CompletableFuture<String> sendToWorker(Map<String, Object> payload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // add shared fields
                payload.putIfAbsent("callbackUrl", lambdaCallbackBaseUrl);
                if (lambdaWorkerSecret != null && !lambdaWorkerSecret.isEmpty()) {
                    payload.putIfAbsent("workerSecret", lambdaWorkerSecret);
                }

                String json = objectMapper.writeValueAsString(payload);
                InvokeRequest request = InvokeRequest.builder()
                        .functionName(lambdaFunctionName)
                        .payload(SdkBytes.fromString(json, StandardCharsets.UTF_8))
                        .build();

                InvokeResponse response = getLambdaClient().invoke(request);
                int statusCode = response.statusCode();
                log.info("Invoked Lambda function {} with status {}", lambdaFunctionName, statusCode);
                return "SENT";
            } catch (Exception e) {
                log.error("Failed to invoke Lambda function {}", lambdaFunctionName, e);
                throw new RuntimeException("Failed to invoke Lambda", e);
            }
        });
    }

    /**
     * Gửi job mockup sang Lambda Worker với đầy đủ options nâng cao
     */
    public void sendMockupJobToLambda(MockupJob job, MockupRequestDto request) {
        log.info("Preparing to send MockupJob to Lambda: jobId={}, backgroundUrl={}, designUrl={}", 
                job.getJobId(), job.getBackgroundUrl(), job.getDesignUrl());
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("jobId", job.getJobId().toString());
        payload.put("backgroundUrl", job.getBackgroundUrl());
        payload.put("designUrl", job.getDesignUrl());

        // Basic options
        Map<String, Object> options = new HashMap<>();
        options.put("designX", job.getDesignX());
        options.put("designY", job.getDesignY());
        options.put("designScale", job.getDesignScale());
        options.put("designRotation", job.getDesignRotation());
        options.put("outputFormat", job.getOutputFormat());
        options.put("outputQuality", job.getOutputQuality());
        options.put("opacity", job.getOpacity());

        // Advanced options
        if (request.getRemoveBackground() != null) {
            options.put("removeBackground", request.getRemoveBackground());
        }
        if (request.getWarp() != null) {
            options.put("warp", request.getWarp());
        }
        if (request.getDisplace() != null) {
            options.put("displace", request.getDisplace());
        }

        payload.put("options", options);
        // Ensure callback and secret are present for single mockup payloads
        if (lambdaCallbackBaseUrl != null && !lambdaCallbackBaseUrl.isEmpty()) {
            payload.put("callbackUrl", lambdaCallbackBaseUrl);
        }
        if (lambdaWorkerSecret != null && !lambdaWorkerSecret.isEmpty()) {
            payload.put("workerSecret", lambdaWorkerSecret);
        }

        log.info("Sending payload to Lambda worker: jobId={}, callbackUrl={}", 
                job.getJobId(), lambdaCallbackBaseUrl);
        
        // fire-and-forget
        sendToWorker(payload);
        
        log.info("MockupJob payload sent to Lambda worker: jobId={}", job.getJobId());
    }

    /**
     * Gửi batch upload job sang Lambda Worker (cho upload thông thường) - async
     */
    public CompletableFuture<String> invokeUploadProcessorAsync(String jobId, List<Map<String, Object>> items) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("jobId", jobId);
        payload.put("items", items);
        // Include callbackUrl and workerSecret at top-level so worker can callback per-item
        if (lambdaCallbackBaseUrl != null && !lambdaCallbackBaseUrl.isEmpty()) {
            payload.put("callbackUrl", lambdaCallbackBaseUrl);
        }
        if (lambdaWorkerSecret != null && !lambdaWorkerSecret.isEmpty()) {
            payload.put("workerSecret", lambdaWorkerSecret);
        }
        return sendToWorker(payload);
    }

    /**
     * Gửi batch mockup job sang Lambda Worker với format requests - async
     */
    public CompletableFuture<String> invokeBatchMockupProcessorAsync(String jobId, List<Map<String, Object>> requests) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("jobId", jobId);
        payload.put("requests", requests); // Sử dụng "requests" thay vì "items"
        // Include callbackUrl and workerSecret at top-level so worker can callback per-item
        if (lambdaCallbackBaseUrl != null && !lambdaCallbackBaseUrl.isEmpty()) {
            payload.put("callbackUrl", lambdaCallbackBaseUrl);
        }
        if (lambdaWorkerSecret != null && !lambdaWorkerSecret.isEmpty()) {
            payload.put("workerSecret", lambdaWorkerSecret);
        }
        return sendToWorker(payload);
    }

    /**
     * Kiểm tra Lambda có sẵn không bằng cách gọi GetFunction
     */
    public boolean isLambdaHealthy() {
        try {
            GetFunctionResponse resp = getLambdaClient().getFunction(
                    GetFunctionRequest.builder().functionName(lambdaFunctionName).build()
            );
            return resp != null && resp.configuration() != null;
        } catch (Exception e) {
            log.warn("Lambda health check failed: {}", e.getMessage());
            return false;
        }
    }
}