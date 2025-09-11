package com.example.imageservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthStatusResponse {
    
    private String status; // UP, DOWN
    private Long pendingJobs;
    private Long inProgressJobs;
    private Long sentToLambdaJobs;
    private Long totalActiveJobs;
    private Boolean lambdaHealthy;
    private Boolean databaseHealthy;
    private Boolean redisHealthy;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
}