package com.example.imageservice.dto;

import com.example.imageservice.entity.MockupJob;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class MockupResponseDto {
    private UUID jobId;
    private MockupJob.JobStatus status;
    private String resultUrl;
    private String message;
}
