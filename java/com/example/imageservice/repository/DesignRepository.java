package com.example.imageservice.repository;

import com.example.imageservice.entity.Design;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Design Repository
 * Data access layer for Design entities
 */
@Repository
public interface DesignRepository extends JpaRepository<Design, String> {

    /**
     * Find designs by user ID
     */
    Page<Design> findByUserId(String userId, Pageable pageable);

    /**
     * Find designs by category
     */
    Page<Design> findByCategory(String category, Pageable pageable);

    /**
     * Find designs by user ID and category
     */
    Page<Design> findByUserIdAndCategory(String userId, String category, Pageable pageable);

    /**
     * Find public designs
     */
    Page<Design> findByIsPublicTrue(Pageable pageable);

    /**
     * Find designs by status
     */
    List<Design> findByStatus(Design.DesignStatus status);

    /**
     * Find designs by quality level
     */
    Page<Design> findByQualityLevel(String qualityLevel, Pageable pageable);

    /**
     * Find designs with minimum quality score
     */
    @Query("SELECT d FROM Design d WHERE d.qualityScore >= :minScore")
    Page<Design> findByMinimumQualityScore(@Param("minScore") Double minScore, Pageable pageable);

    /**
     * Find designs by original mockup ID
     */
    List<Design> findByOriginalMockupId(String originalMockupId);

    /**
     * Find designs created between dates
     */
    @Query("SELECT d FROM Design d WHERE d.createdAt BETWEEN :startDate AND :endDate")
    Page<Design> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, 
                                       @Param("endDate") LocalDateTime endDate, 
                                       Pageable pageable);

    /**
     * Find designs by tags (contains any of the tags)
     */
    @Query("SELECT d FROM Design d WHERE d.tags LIKE %:tag%")
    Page<Design> findByTagsContaining(@Param("tag") String tag, Pageable pageable);

    /**
     * Find designs by multiple tags
     */
    @Query("SELECT d FROM Design d WHERE d.tags LIKE %:tag1% OR d.tags LIKE %:tag2%")
    Page<Design> findByMultipleTags(@Param("tag1") String tag1, @Param("tag2") String tag2, Pageable pageable);

    /**
     * Find designs with embeddings (for similarity search)
     */
    @Query("SELECT d FROM Design d WHERE d.embeddingVector IS NOT NULL AND d.embeddingVector != ''")
    List<Design> findDesignsWithEmbeddings();

    /**
     * Find designs by user ID with embeddings
     */
    @Query("SELECT d FROM Design d WHERE d.userId = :userId AND d.embeddingVector IS NOT NULL AND d.embeddingVector != ''")
    List<Design> findByUserIdWithEmbeddings(@Param("userId") String userId);

    /**
     * Find most downloaded designs
     */
    @Query("SELECT d FROM Design d ORDER BY d.downloadCount DESC")
    Page<Design> findMostDownloaded(Pageable pageable);

    /**
     * Find most viewed designs
     */
    @Query("SELECT d FROM Design d ORDER BY d.viewCount DESC")
    Page<Design> findMostViewed(Pageable pageable);

    /**
     * Find recent designs
     */
    @Query("SELECT d FROM Design d ORDER BY d.createdAt DESC")
    Page<Design> findRecentDesigns(Pageable pageable);

    /**
     * Count designs by user ID
     */
    long countByUserId(String userId);

    /**
     * Count designs by category
     */
    long countByCategory(String category);

    /**
     * Count designs by status
     */
    long countByStatus(Design.DesignStatus status);

    /**
     * Count designs with embeddings
     */
    @Query("SELECT COUNT(d) FROM Design d WHERE d.embeddingVector IS NOT NULL AND d.embeddingVector != ''")
    long countDesignsWithEmbeddings();

    /**
     * Find designs by search term (name, description, tags)
     */
    @Query("SELECT d FROM Design d WHERE " +
           "LOWER(d.designName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(d.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(d.tags) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Design> findBySearchTerm(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Find designs by user ID and search term
     */
    @Query("SELECT d FROM Design d WHERE d.userId = :userId AND " +
           "(LOWER(d.designName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(d.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(d.tags) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<Design> findByUserIdAndSearchTerm(@Param("userId") String userId, 
                                          @Param("searchTerm") String searchTerm, 
                                          Pageable pageable);

    /**
     * Find designs with high similarity potential (for duplicate detection)
     */
    @Query("SELECT d FROM Design d WHERE d.qualityScore >= 0.7 AND d.embeddingVector IS NOT NULL")
    List<Design> findHighQualityDesignsWithEmbeddings();

    /**
     * Delete designs by user ID
     */
    void deleteByUserId(String userId);

    /**
     * Delete designs by original mockup ID
     */
    void deleteByOriginalMockupId(String originalMockupId);

    /**
     * Find designs created by user in date range
     */
    @Query("SELECT d FROM Design d WHERE d.userId = :userId AND d.createdAt BETWEEN :startDate AND :endDate")
    List<Design> findByUserIdAndCreatedAtBetween(@Param("userId") String userId,
                                                @Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);
}
