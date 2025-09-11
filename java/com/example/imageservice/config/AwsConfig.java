package com.example.imageservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;

import java.time.Duration;

@Configuration
public class AwsConfig {
    
    @Value("${aws.region:us-east-1}")
    private String awsRegion;
    
    @Bean
    public LambdaClient lambdaClient() {
        return LambdaClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(builder -> builder
                        .apiCallTimeout(Duration.ofMinutes(5))
                        .apiCallAttemptTimeout(Duration.ofMinutes(3))
                )
                .build();
    }
    
    // S3 client is optional; enable when storage integration is finalized

    // DynamoDB client removed - no longer needed
    // All data is now managed by Spring Boot with JPA/Hibernate
}