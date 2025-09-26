package com.example.imageservice.repository;

import com.example.imageservice.entity.DesignSimilarity;
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
 * Design Similarity Repository
 * Data access layer for DesignSimilarity entities
 */
@Repository
public interface DesignSimilarityRepository extends JpaRepository<DesignSimilarity, String> {

    /**
     * Find similarities by design1 ID
     */
    Page<DesignSimilarity> findByDesign1Id(String design1Id, Pageable pageable);

    /**
     * Find similarities by design2 ID
     */
    Page<DesignSimilarity> findByDesign2Id(String design2Id, Pageable pageable);

    /**
     * Find similarities between two specific designs
     */
    Optional<DesignSimilarity> findByDesign1IdAndDesign2Id(String design1Id, String design2Id);

    /**
     * Find similarities by similarity type
     */
    Page<DesignSimilarity> findBySimilarityType(DesignSimilarity.SimilarityType similarityType, Pageable pageable);

    /**
     * Find similarities with minimum score
     */
    @Query("SELECT ds FROM DesignSimilarity ds WHERE ds.similarityScore >= :minScore")
    Page<DesignSimilarity> findByMinimumSimilarityScore(@Param("minScore") Double minScore, Pageable pageable);

    /**
     * Find similarities with high similarity (potential duplicates)
     */
    @Query("SELECT ds FROM DesignSimilarity ds WHERE ds.similarityScore >= 0.85")
    List<DesignSimilarity> findHighSimilarityDesigns();

    /**
     * Find potential duplicates for a design
     */
    @Query("SELECT ds FROM DesignSimilarity ds WHERE " +
           "(ds.design1Id = :designId OR ds.design2Id = :designId) AND " +
           "ds.similarityScore >= 0.85")
    List<DesignSimilarity> findPotentialDuplicates(@Param("designId") String designId);

    /**
     * Find verified similarities
     */
    Page<DesignSimilarity> findByIsVerifiedTrue(Pageable pageable);

    /**
     * Find unverified similarities
     */
    Page<DesignSimilarity> findByIsVerifiedFalse(Pageable pageable);

    /**
     * Find similarities by algorithm used
     */
    Page<DesignSimilarity> findByAlgorithmUsed(String algorithmUsed, Pageable pageable);

    /**
     * Find similarities with minimum confidence level
     */
    @Query("SELECT ds FROM DesignSimilarity ds WHERE ds.confidenceLevel >= :minConfidence")
    Page<DesignSimilarity> findByMinimumConfidenceLevel(@Param("minConfidence") Double minConfidence, Pageable pageable);

    /**
     * Find similarities created between dates
     */
    @Query("SELECT ds FROM DesignSimilarity ds WHERE ds.createdAt BETWEEN :startDate AND :endDate")
    Page<DesignSimilarity> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, 
                                                 @Param("endDate") LocalDateTime endDate, 
                                                 Pageable pageable);

    /**
     * Find top similar designs for a given design
     */
    @Query("SELECT ds FROM DesignSimilarity ds WHERE " +
           "(ds.design1Id = :designId OR ds.design2Id = :designId) AND " +
           "ds.similarityScore >= :minScore " +
           "ORDER BY ds.similarityScore DESC")
    List<DesignSimilarity> findTopSimilarDesigns(@Param("designId") String designId, 
                                                @Param("minScore") Double minScore, 
                                                Pageable pageable);

    /**
     * Find all similarities for a design (both directions)
     */
    @Query("SELECT ds FROM DesignSimilarity ds WHERE ds.design1Id = :designId OR ds.design2Id = :designId")
    List<DesignSimilarity> findAllSimilaritiesForDesign(@Param("designId") String designId);

    /**
     * Find similarities by verified user
     */
    Page<DesignSimilarity> findByVerifiedBy(String verifiedBy, Pageable pageable);

    /**
     * Find similarities verified between dates
     */
    @Query("SELECT ds FROM DesignSimilarity ds WHERE ds.verifiedAt BETWEEN :startDate AND :endDate")
    Page<DesignSimilarity> findByVerifiedAtBetween(@Param("startDate") LocalDateTime startDate, 
                                                  @Param("endDate") LocalDateTime endDate, 
                                                  Pageable pageable);

    /**
     * Count similarities by design ID
     */
    @Query("SELECT COUNT(ds) FROM DesignSimilarity ds WHERE ds.design1Id = :designId OR ds.design2Id = :designId")
    long countSimilaritiesForDesign(@Param("designId") String designId);

    /**
     * Count high similarity designs
     */
    @Query("SELECT COUNT(ds) FROM DesignSimilarity ds WHERE ds.similarityScore >= 0.85")
    long countHighSimilarityDesigns();

    /**
     * Count verified similarities
     */
    long countByIsVerifiedTrue();

    /**
     * Count unverified similarities
     */
    long countByIsVerifiedFalse();

    /**
     * Find similarities that need verification (high score but not verified)
     */
    @Query("SELECT ds FROM DesignSimilarity ds WHERE ds.similarityScore >= 0.8 AND ds.isVerified = false")
    List<DesignSimilarity> findSimilaritiesNeedingVerification();

    /**
     * Delete similarities by design ID (both directions)
     */
    @Query("DELETE FROM DesignSimilarity ds WHERE ds.design1Id = :designId OR ds.design2Id = :designId")
    void deleteByDesignId(@Param("designId") String designId);

    /**
     * Delete old similarities (older than specified date)
     */
    @Query("DELETE FROM DesignSimilarity ds WHERE ds.createdAt < :cutoffDate")
    void deleteOldSimilarities(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find duplicate similarities (same design pair with different IDs)
     */
    @Query("SELECT ds1 FROM DesignSimilarity ds1, DesignSimilarity ds2 WHERE " +
           "ds1.id != ds2.id AND " +
           "((ds1.design1Id = ds2.design1Id AND ds1.design2Id = ds2.design2Id) OR " +
           "(ds1.design1Id = ds2.design2Id AND ds1.design2Id = ds2.design1Id))")
    List<DesignSimilarity> findDuplicateSimilarities();

    /**
     * Find similarities by score range
     */
    @Query("SELECT ds FROM DesignSimilarity ds WHERE ds.similarityScore BETWEEN :minScore AND :maxScore")
    Page<DesignSimilarity> findBySimilarityScoreBetween(@Param("minScore") Double minScore, 
                                                       @Param("maxScore") Double maxScore, 
                                                       Pageable pageable);

    /**
     * Find most similar designs (top N)
     */
    @Query("SELECT ds FROM DesignSimilarity ds ORDER BY ds.similarityScore DESC")
    Page<DesignSimilarity> findMostSimilarDesigns(Pageable pageable);

    /**
     * Find similarities with specific algorithm and score range
     */
    @Query("SELECT ds FROM DesignSimilarity ds WHERE ds.algorithmUsed = :algorithm AND " +
           "ds.similarityScore BETWEEN :minScore AND :maxScore")
    List<DesignSimilarity> findByAlgorithmAndScoreRange(@Param("algorithm") String algorithm,
                                                       @Param("minScore") Double minScore,
                                                       @Param("maxScore") Double maxScore);
}
