package com.example.imageservice.controller;

import com.example.imageservice.service.ImageMagickService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@Slf4j
public class TestController {
    
    private final ImageMagickService imageMagickService;
    
    @GetMapping("/imagemagick")
    public ResponseEntity<Map<String, Object>> testImageMagick() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean isAvailable = imageMagickService.isAvailable();
            String version = imageMagickService.getVersion();
            boolean testCommand = imageMagickService.testCommand();
            
            result.put("available", isAvailable);
            result.put("version", version);
            result.put("testCommand", testCommand);
            result.put("status", "success");
            
        } catch (Exception e) {
            log.error("Test failed", e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
}
