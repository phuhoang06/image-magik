package com.example.imageservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageMagickService {
    
    private final CommandRunner commandRunner;
    
    /**
     * Kiểm tra ImageMagick có sẵn không
     */
    public boolean isAvailable() {
        return commandRunner.isImageMagickAvailable();
    }
    
    /**
     * Lấy version của ImageMagick
     */
    public String getVersion() {
        return commandRunner.getImageMagickVersion();
    }
    
    /**
     * Test chạy lệnh đơn giản
     */
    public boolean testCommand() {
        try {
            var result = commandRunner.runCommand(new String[]{"magick", "-version"}, 10);
            return result.isSuccess();
        } catch (Exception e) {
            log.error("Test command failed", e);
            return false;
        }
    }
}
