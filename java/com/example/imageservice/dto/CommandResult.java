package com.example.imageservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandResult {
    
    private int exitCode;
    private String output;
    private String error;
    private boolean success;
    private long executionTimeMs;
    
    /**
     * Kiểm tra command có thành công không
     */
    public boolean isSuccess() {
        return success && exitCode == 0;
    }
    
    /**
     * Lấy error message nếu có
     */
    public String getErrorMessage() {
        if (error != null && !error.isEmpty()) {
            return error;
        }
        if (!success && exitCode != 0) {
            return "Command failed with exit code: " + exitCode;
        }
        return null;
    }
}
