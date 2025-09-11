package com.example.imageservice.dto;

import com.example.imageservice.entity.UploadItem;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Upload Request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadRequest {
    
    @NotNull(message = "URLs list cannot be null")
    @NotEmpty(message = "URLs list cannot be empty")
    @Size(max = 50, message = "Maximum 50 URLs allowed per request")
    private List<@NotNull(message = "URL cannot be null") String> urls;
}