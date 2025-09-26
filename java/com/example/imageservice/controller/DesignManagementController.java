package com.example.imageservice.controller;

import com.example.imageservice.entity.Design;
import com.example.imageservice.entity.DesignSimilarity;
import com.example.imageservice.repository.DesignRepository;
import com.example.imageservice.repository.DesignSimilarityRepository;
import com.example.imageservice.service.DesignQualityValidationService;
import com.example.imageservice.service.MultiDesignDetectionService;
import com.example.imageservice.service.SimilaritySearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Design Management Controller
 * Handles CRUD operations for designs and similarity management
 */
@RestController
@RequestMapping("/api/v1/designs")
@RequiredArgsConstructor
@Slf4j
public class DesignManagementController {

    private final DesignRepository designRepository;
    private final DesignSimilarityRepository similarityRepository;
    private final MultiDesignDetectionService multiDesignDetectionService;
    private final DesignQualityValidationService qualityValidationService;
    private final SimilaritySearchService similaritySearchService;

    /**
     * Get all designs with pagination and filtering
     * GET /api/v1/designs
     */
    @GetMapping
    public ResponseEntity<?> getAllDesigns(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String qualityLevel,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isPublic) {
        
        try {
            String userId = getUserId(jwt);
            log.info("Get designs request from user: {}, page: {}, size: {}", userId, page, size);
            
            Sort sort = sortDir.equalsIgnoreCase("desc") ? 
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            
            Page<Design> designs;
            
            if (search != null && !search.trim().isEmpty()) {
                designs = designRepository.findByUserIdAndSearchTerm(userId, search.trim(), pageable);
            } else if (category != null && !category.trim().isEmpty()) {
                designs = designRepository.findByUserIdAndCategory(userId, category.trim(), pageable);
            } else {
                designs = designRepository.findByUserId(userId, pageable);
            }
            
            // Apply additional filters
            if (qualityLevel != null || isPublic != null) {
                List<Design> filteredDesigns = designs.getContent().stream()
                    .filter(design -> qualityLevel == null || qualityLevel.equals(design.getQualityLevel()))
                    .filter(design -> isPublic == null || isPublic.equals(design.getIsPublic()))
                    .collect(Collectors.toList());
                
                // Create custom page response
                Map<String, Object> response = Map.of(
                    "content", filteredDesigns,
                    "totalElements", filteredDesigns.size(),
                    "totalPages", 1,
                    "size", size,
                    "number", page,
                    "first", page == 0,
                    "last", true,
                    "numberOfElements", filteredDesigns.size()
                );
                return ResponseEntity.ok(response);
            }
            
            return ResponseEntity.ok(designs);
            
        } catch (Exception e) {
            log.error("Error getting designs: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "GET_DESIGNS_FAILED", "message", e.getMessage()));
        }
    }

    /**
     * Get design by ID
     * GET /api/v1/designs/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getDesignById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        
        try {
            String userId = getUserId(jwt);
            log.info("Get design request from user: {} for design: {}", userId, id);
            
            Optional<Design> designOpt = designRepository.findById(id);
            if (designOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Design design = designOpt.get();
            
            // Check if user has access to this design
            if (!design.getUserId().equals(userId) && !Boolean.TRUE.equals(design.getIsPublic())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "ACCESS_DENIED", "message", "You don't have access to this design"));
            }
            
            // Increment view count
            design.incrementViewCount();
            designRepository.save(design);
            
            return ResponseEntity.ok(design);
            
        } catch (Exception e) {
            log.error("Error getting design: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "GET_DESIGN_FAILED", "message", e.getMessage()));
        }
    }

    /**
     * Update design
     * PUT /api/v1/designs/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateDesign(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @RequestBody Map<String, Object> updates) {
        
        try {
            String userId = getUserId(jwt);
            log.info("Update design request from user: {} for design: {}", userId, id);
            
            Optional<Design> designOpt = designRepository.findById(id);
            if (designOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Design design = designOpt.get();
            
            // Check if user owns this design
            if (!design.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "ACCESS_DENIED", "message", "You can only update your own designs"));
            }
            
            // Update allowed fields
            if (updates.containsKey("designName")) {
                design.setDesignName((String) updates.get("designName"));
            }
            if (updates.containsKey("description")) {
                design.setDescription((String) updates.get("description"));
            }
            if (updates.containsKey("category")) {
                design.setCategory((String) updates.get("category"));
            }
            if (updates.containsKey("subcategory")) {
                design.setSubcategory((String) updates.get("subcategory"));
            }
            if (updates.containsKey("tags")) {
                if (updates.get("tags") instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> tags = (List<String>) updates.get("tags");
                    design.setTagsFromArray(tags.toArray(new String[0]));
                }
            }
            if (updates.containsKey("isPublic")) {
                design.setIsPublic((Boolean) updates.get("isPublic"));
            }
            
            design.setUpdatedBy(userId);
            Design savedDesign = designRepository.save(design);
            
            return ResponseEntity.ok(savedDesign);
            
        } catch (Exception e) {
            log.error("Error updating design: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "UPDATE_DESIGN_FAILED", "message", e.getMessage()));
        }
    }

    /**
     * Delete design
     * DELETE /api/v1/designs/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDesign(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        
        try {
            String userId = getUserId(jwt);
            log.info("Delete design request from user: {} for design: {}", userId, id);
            
            Optional<Design> designOpt = designRepository.findById(id);
            if (designOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Design design = designOpt.get();
            
            // Check if user owns this design
            if (!design.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "ACCESS_DENIED", "message", "You can only delete your own designs"));
            }
            
            // Delete related similarities
            similarityRepository.deleteByDesignId(id);
            
            // Delete design
            designRepository.delete(design);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Design deleted successfully",
                "deletedDesignId", id
            ));
            
        } catch (Exception e) {
            log.error("Error deleting design: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "DELETE_DESIGN_FAILED", "message", e.getMessage()));
        }
    }

    /**
     * Get similar designs for a given design
     * GET /api/v1/designs/{id}/similar
     */
    @GetMapping("/{id}/similar")
    public ResponseEntity<?> getSimilarDesigns(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0.7") double minScore) {
        
        try {
            String userId = getUserId(jwt);
            log.info("Get similar designs request from user: {} for design: {}, limit: {}", userId, id, limit);
            
            Optional<Design> designOpt = designRepository.findById(id);
            if (designOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Design design = designOpt.get();
            
            // Check if user has access to this design
            if (!design.getUserId().equals(userId) && !Boolean.TRUE.equals(design.getIsPublic())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "ACCESS_DENIED", "message", "You don't have access to this design"));
            }
            
            // Get similar designs from database
            Pageable pageable = PageRequest.of(0, limit);
            List<DesignSimilarity> similarities = similarityRepository.findTopSimilarDesigns(id, minScore, pageable);
            
            // Get the actual design objects
            List<Map<String, Object>> similarDesigns = new ArrayList<>();
            for (DesignSimilarity similarity : similarities) {
                String similarDesignId = similarity.getDesign1Id().equals(id) ? 
                    similarity.getDesign2Id() : similarity.getDesign1Id();
                
                Optional<Design> similarDesignOpt = designRepository.findById(similarDesignId);
                if (similarDesignOpt.isPresent()) {
                    Design similarDesign = similarDesignOpt.get();
                    
                    // Check access
                    if (similarDesign.getUserId().equals(userId) || Boolean.TRUE.equals(similarDesign.getIsPublic())) {
                        Map<String, Object> designInfo = Map.of(
                            "design", similarDesign,
                            "similarityScore", similarity.getSimilarityScore(),
                            "similarityType", similarity.getSimilarityType(),
                            "isVerified", similarity.getIsVerified()
                        );
                        similarDesigns.add(designInfo);
                    }
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "originalDesignId", id,
                "similarDesigns", similarDesigns,
                "totalFound", similarDesigns.size(),
                "minScore", minScore
            ));
            
        } catch (Exception e) {
            log.error("Error getting similar designs: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "GET_SIMILAR_DESIGNS_FAILED", "message", e.getMessage()));
        }
    }

    /**
     * Get design statistics
     * GET /api/v1/designs/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getDesignStats(@AuthenticationPrincipal Jwt jwt) {
        try {
            String userId = getUserId(jwt);
            log.info("Get design stats request from user: {}", userId);
            
            long totalDesigns = designRepository.countByUserId(userId);
            long designsWithEmbeddings = designRepository.findByUserIdWithEmbeddings(userId).size();
            long publicDesigns = designRepository.findByUserId(userId, PageRequest.of(0, 1))
                .getContent().stream()
                .mapToLong(design -> Boolean.TRUE.equals(design.getIsPublic()) ? 1 : 0)
                .sum();
            
            // Get quality distribution
            Map<String, Long> qualityDistribution = new HashMap<>();
            qualityDistribution.put("EXCELLENT", designRepository.findByUserId(userId, PageRequest.of(0, Integer.MAX_VALUE))
                .getContent().stream()
                .mapToLong(design -> "EXCELLENT".equals(design.getQualityLevel()) ? 1 : 0)
                .sum());
            qualityDistribution.put("GOOD", designRepository.findByUserId(userId, PageRequest.of(0, Integer.MAX_VALUE))
                .getContent().stream()
                .mapToLong(design -> "GOOD".equals(design.getQualityLevel()) ? 1 : 0)
                .sum());
            qualityDistribution.put("ACCEPTABLE", designRepository.findByUserId(userId, PageRequest.of(0, Integer.MAX_VALUE))
                .getContent().stream()
                .mapToLong(design -> "ACCEPTABLE".equals(design.getQualityLevel()) ? 1 : 0)
                .sum());
            qualityDistribution.put("POOR", designRepository.findByUserId(userId, PageRequest.of(0, Integer.MAX_VALUE))
                .getContent().stream()
                .mapToLong(design -> "POOR".equals(design.getQualityLevel()) ? 1 : 0)
                .sum());
            
            Map<String, Object> stats = Map.of(
                "totalDesigns", totalDesigns,
                "designsWithEmbeddings", designsWithEmbeddings,
                "publicDesigns", publicDesigns,
                "qualityDistribution", qualityDistribution,
                "userId", userId
            );
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Error getting design stats: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "GET_STATS_FAILED", "message", e.getMessage()));
        }
    }

    /**
     * Extract user ID from JWT token
     */
    private String getUserId(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("Missing authentication");
        }
        String sub = jwt.getSubject();
        if (sub != null && !sub.isBlank()) {
            return sub;
        }
        String email = jwt.getClaim("email");
        if (email != null && !email.isBlank()) {
            return email;
        }
        String username = jwt.getClaim("username");
        if (username != null && !username.isBlank()) {
            return username;
        }
        return "anonymous";
    }
}
