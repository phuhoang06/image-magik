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
public class AdminStatsResponse {
    
    private Long totalJobs;
    private Long totalItems;
    private Long completedItems;
    private Long failedItems;
    private Double overallSuccessRate;
    
    private Long jobsLast24Hours;
    private Long jobsLast7Days;
    private Long jobsLast30Days;
    
    private Long activeUsers;
    private Long totalUsers;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
}