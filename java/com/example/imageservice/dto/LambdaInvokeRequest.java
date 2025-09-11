package com.example.imageservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LambdaInvokeRequest {
    private String jobId;
    private String userId;
    private List<String> urls;
    private String callbackUrl;
}