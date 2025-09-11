package com.example.imageservice.service;

import com.example.imageservice.dto.CommandResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CommandRunner {
    
    /**
     * Chạy lệnh với timeout
     */
    public CommandResult runCommand(String[] command, long timeoutSeconds) throws IOException, InterruptedException {
        return runCommand(command, timeoutSeconds, null);
    }
    
    /**
     * Chạy lệnh với timeout và working directory
     */
    public CommandResult runCommand(String[] command, long timeoutSeconds, String workingDirectory) 
            throws IOException, InterruptedException {
        
        log.debug("Running command: {}", String.join(" ", command));
        
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        
        // Set working directory nếu có
        if (workingDirectory != null) {
            processBuilder.directory(new java.io.File(workingDirectory));
        }
        
        // Redirect error stream để merge với output
        processBuilder.redirectErrorStream(true);
        
        Process process = processBuilder.start();
        
        // Đọc output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        // Chờ process hoàn thành với timeout
        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        
        if (!completed) {
            log.warn("Command timed out after {} seconds, destroying process", timeoutSeconds);
            process.destroyForcibly();
            return CommandResult.builder()
                    .exitCode(-1)
                    .output(output.toString())
                    .error("Command timed out after " + timeoutSeconds + " seconds")
                    .success(false)
                    .build();
        }
        
        int exitCode = process.exitValue();
        boolean success = exitCode == 0;
        
        CommandResult result = CommandResult.builder()
                .exitCode(exitCode)
                .output(output.toString())
                .success(success)
                .build();
        
        if (success) {
            log.debug("Command completed successfully with exit code: {}", exitCode);
        } else {
            log.warn("Command failed with exit code: {} and output: {}", exitCode, output);
        }
        
        return result;
    }
    
    /**
     * Chạy lệnh ImageMagick cụ thể
     */
    public CommandResult runImageMagickCommand(String[] command) throws IOException, InterruptedException {
        // Timeout mặc định 5 phút cho ImageMagick
        return runCommand(command, 300);
    }
    
    /**
     * Kiểm tra ImageMagick có sẵn không
     */
    public boolean isImageMagickAvailable() {
        try {
            CommandResult result = runCommand(new String[]{"magick", "-version"}, 10);
            return result.isSuccess();
        } catch (Exception e) {
            log.warn("ImageMagick not available: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Lấy version của ImageMagick
     */
    public String getImageMagickVersion() {
        try {
            CommandResult result = runCommand(new String[]{"magick", "-version"}, 10);
            if (result.isSuccess()) {
                return result.getOutput().trim();
            }
        } catch (Exception e) {
            log.warn("Failed to get ImageMagick version: {}", e.getMessage());
        }
        return "Unknown";
    }
}
