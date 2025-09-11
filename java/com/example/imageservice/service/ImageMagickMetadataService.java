package com.example.imageservice.service;

import com.example.imageservice.entity.UploadItem;
import com.example.imageservice.repository.UploadItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageMagickMetadataService {
    
    private final UploadItemRepository uploadItemRepository;
    
    @Value("${app.imagemagick.enabled:false}")
    private boolean imageMagickEnabled;
    
    @Value("${app.imagemagick.identify-path:identify}")
    private String identifyPath;
    
    @Value("${app.temp.directory:/tmp}")
    private String tempDirectory;
    
    /**
     * Enrich metadata cho một ảnh bằng ImageMagick
     */
    public Optional<EnrichedMetadata> enrichMetadata(String itemId) {
        if (!imageMagickEnabled) {
            log.warn("ImageMagick is disabled for item: {}", itemId);
            return Optional.empty();
        }
        
        try {
            // Tìm item theo itemId (sử dụng JpaRepository.findById)
            UploadItem item = uploadItemRepository.findById(itemId)
                    .orElseThrow(() -> new RuntimeException("Item not found: " + itemId));
            
            if (item.getCdnUrl() == null) {
                log.warn("Item {} has no CDN URL", itemId);
                return Optional.empty();
            }
            
            return enrichMetadataFromUrl(item.getCdnUrl());
            
        } catch (Exception e) {
            log.error("Error enriching metadata for item {}: {}", itemId, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Enrich metadata từ URL ảnh
     */
    public Optional<EnrichedMetadata> enrichMetadataFromUrl(String imageUrl) {
        if (!imageMagickEnabled) {
            return Optional.empty();
        }
        
        Path tempFile = null;
        try {
            // Download ảnh về temp file
            tempFile = downloadImageToTemp(imageUrl);
            
            // Chạy ImageMagick identify
            String identifyOutput = runImageMagickIdentify(tempFile);
            
            // Parse output thành metadata
            EnrichedMetadata metadata = parseIdentifyOutput(identifyOutput);
            
            log.info("Successfully enriched metadata for image: {}", imageUrl);
            return Optional.of(metadata);
            
        } catch (Exception e) {
            log.error("Error enriching metadata from URL {}: {}", imageUrl, e.getMessage(), e);
            return Optional.empty();
        } finally {
            // Cleanup temp file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Could not delete temp file: {}", tempFile, e);
                }
            }
        }
    }
    
    /**
     * Download ảnh về temp file
     */
    private Path downloadImageToTemp(String imageUrl) throws IOException {
        Path tempFile = Files.createTempFile(Path.of(tempDirectory), "img_", ".tmp");
        
        try (var inputStream = new URL(imageUrl).openStream()) {
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        
        return tempFile;
    }
    
    /**
     * Chạy ImageMagick identify command
     */
    private String runImageMagickIdentify(Path imagePath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                identifyPath, "-verbose", imagePath.toString()
        );
        
        Process process = pb.start();
        
        // Đọc output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        // Đọc error output
        StringBuilder errorOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
        }
        
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            log.warn("ImageMagick identify failed with exit code {}: {}", 
                    exitCode, errorOutput.toString());
        }
        
        return output.toString();
    }
    
    /**
     * Parse output từ ImageMagick identify
     */
    private EnrichedMetadata parseIdentifyOutput(String output) {
        EnrichedMetadata metadata = new EnrichedMetadata();
        
        // Parse EXIF data
        parseExifData(output, metadata);
        
        // Parse color profile
        parseColorProfile(output, metadata);
        
        // Parse compression info
        parseCompressionInfo(output, metadata);
        
        // Parse other technical details
        parseTechnicalDetails(output, metadata);
        
        return metadata;
    }
    
    /**
     * Parse EXIF data
     */
    private void parseExifData(String output, EnrichedMetadata metadata) {
        // GPS coordinates
        Pattern gpsPattern = Pattern.compile("exif:GPSLatitude: ([^\\n]+)");
        Matcher gpsMatcher = gpsPattern.matcher(output);
        if (gpsMatcher.find()) {
            metadata.setGpsLatitude(parseGpsCoordinate(gpsMatcher.group(1)));
        }
        
        gpsPattern = Pattern.compile("exif:GPSLongitude: ([^\\n]+)");
        gpsMatcher = gpsPattern.matcher(output);
        if (gpsMatcher.find()) {
            metadata.setGpsLongitude(parseGpsCoordinate(gpsMatcher.group(1)));
        }
        
        // Camera info
        Pattern cameraPattern = Pattern.compile("exif:Make: ([^\\n]+)");
        Matcher cameraMatcher = cameraPattern.matcher(output);
        if (cameraMatcher.find()) {
            metadata.setCameraMake(cameraMatcher.group(1).trim());
        }
        
        cameraPattern = Pattern.compile("exif:Model: ([^\\n]+)");
        cameraMatcher = cameraPattern.matcher(output);
        if (cameraMatcher.find()) {
            metadata.setCameraModel(cameraMatcher.group(1).trim());
        }
        
        // Lens info
        Pattern lensPattern = Pattern.compile("exif:FocalLength: ([^\\n]+)");
        Matcher lensMatcher = lensPattern.matcher(output);
        if (lensMatcher.find()) {
            metadata.setFocalLength(lensMatcher.group(1).trim());
        }
        
        // Date taken
        Pattern datePattern = Pattern.compile("exif:DateTimeOriginal: ([^\\n]+)");
        Matcher dateMatcher = datePattern.matcher(output);
        if (dateMatcher.find()) {
            metadata.setDateTaken(dateMatcher.group(1).trim());
        }
        
        // Orientation
        Pattern orientationPattern = Pattern.compile("exif:Orientation: ([^\\n]+)");
        Matcher orientationMatcher = orientationPattern.matcher(output);
        if (orientationMatcher.find()) {
            try {
                metadata.setOrientation(Integer.parseInt(orientationMatcher.group(1).trim()));
            } catch (NumberFormatException e) {
                log.warn("Could not parse orientation: {}", orientationMatcher.group(1));
            }
        }
    }
    
    /**
     * Parse color profile info
     */
    private void parseColorProfile(String output, EnrichedMetadata metadata) {
        Pattern profilePattern = Pattern.compile("Profile-icc: ([^\\n]+)");
        Matcher profileMatcher = profilePattern.matcher(output);
        if (profileMatcher.find()) {
            metadata.setIccProfile(profileMatcher.group(1).trim());
        }
        
        Pattern colorSpacePattern = Pattern.compile("Colorspace: ([^\\n]+)");
        Matcher colorSpaceMatcher = colorSpacePattern.matcher(output);
        if (colorSpaceMatcher.find()) {
            metadata.setColorSpace(colorSpaceMatcher.group(1).trim());
        }
    }
    
    /**
     * Parse compression info
     */
    private void parseCompressionInfo(String output, EnrichedMetadata metadata) {
        Pattern compressionPattern = Pattern.compile("Compression: ([^\\n]+)");
        Matcher compressionMatcher = compressionPattern.matcher(output);
        if (compressionMatcher.find()) {
            metadata.setCompression(compressionMatcher.group(1).trim());
        }
        
        Pattern qualityPattern = Pattern.compile("Quality: ([^\\n]+)");
        Matcher qualityMatcher = qualityPattern.matcher(output);
        if (qualityMatcher.find()) {
            try {
                metadata.setQuality(Integer.parseInt(qualityMatcher.group(1).trim()));
            } catch (NumberFormatException e) {
                log.warn("Could not parse quality: {}", qualityMatcher.group(1));
            }
        }
    }
    
    /**
     * Parse technical details
     */
    private void parseTechnicalDetails(String output, EnrichedMetadata metadata) {
        Pattern depthPattern = Pattern.compile("Depth: ([^\\n]+)");
        Matcher depthMatcher = depthPattern.matcher(output);
        if (depthMatcher.find()) {
            metadata.setBitDepth(depthMatcher.group(1).trim());
        }
        
        Pattern formatPattern = Pattern.compile("Format: ([^\\n]+)");
        Matcher formatMatcher = formatPattern.matcher(output);
        if (formatMatcher.find()) {
            metadata.setFormat(formatMatcher.group(1).trim());
        }
    }
    
    /**
     * Parse GPS coordinate từ ImageMagick format
     */
    private Double parseGpsCoordinate(String gpsString) {
        try {
            // ImageMagick format: "51 deg 30' 0.00\" N"
            Pattern pattern = Pattern.compile("(\\d+) deg (\\d+)' ([\\d.]+)\" ([NSWE])");
            Matcher matcher = pattern.matcher(gpsString);
            
            if (matcher.find()) {
                int degrees = Integer.parseInt(matcher.group(1));
                int minutes = Integer.parseInt(matcher.group(2));
                double seconds = Double.parseDouble(matcher.group(3));
                String direction = matcher.group(4);
                
                double decimal = degrees + (minutes / 60.0) + (seconds / 3600.0);
                
                if (direction.equals("S") || direction.equals("W")) {
                    decimal = -decimal;
                }
                
                return decimal;
            }
        } catch (Exception e) {
            log.warn("Could not parse GPS coordinate: {}", gpsString, e);
        }
        
        return null;
    }
    
    /**
     * DTO cho enriched metadata
     */
    public static class EnrichedMetadata {
        // GPS
        private Double gpsLatitude;
        private Double gpsLongitude;
        
        // Camera
        private String cameraMake;
        private String cameraModel;
        private String focalLength;
        
        // Date
        private String dateTaken;
        
        // Technical
        private Integer orientation;
        private String iccProfile;
        private String colorSpace;
        private String compression;
        private Integer quality;
        private String bitDepth;
        private String format;
        
        // Getters and setters
        public Double getGpsLatitude() { return gpsLatitude; }
        public void setGpsLatitude(Double gpsLatitude) { this.gpsLatitude = gpsLatitude; }
        
        public Double getGpsLongitude() { return gpsLongitude; }
        public void setGpsLongitude(Double gpsLongitude) { this.gpsLongitude = gpsLongitude; }
        
        public String getCameraMake() { return cameraMake; }
        public void setCameraMake(String cameraMake) { this.cameraMake = cameraMake; }
        
        public String getCameraModel() { return cameraModel; }
        public void setCameraModel(String cameraModel) { this.cameraModel = cameraModel; }
        
        public String getFocalLength() { return focalLength; }
        public void setFocalLength(String focalLength) { this.focalLength = focalLength; }
        
        public String getDateTaken() { return dateTaken; }
        public void setDateTaken(String dateTaken) { this.dateTaken = dateTaken; }
        
        public Integer getOrientation() { return orientation; }
        public void setOrientation(Integer orientation) { this.orientation = orientation; }
        
        public String getIccProfile() { return iccProfile; }
        public void setIccProfile(String iccProfile) { this.iccProfile = iccProfile; }
        
        public String getColorSpace() { return colorSpace; }
        public void setColorSpace(String colorSpace) { this.colorSpace = colorSpace; }
        
        public String getCompression() { return compression; }
        public void setCompression(String compression) { this.compression = compression; }
        
        public Integer getQuality() { return quality; }
        public void setQuality(Integer quality) { this.quality = quality; }
        
        public String getBitDepth() { return bitDepth; }
        public void setBitDepth(String bitDepth) { this.bitDepth = bitDepth; }
        
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
    }
}
