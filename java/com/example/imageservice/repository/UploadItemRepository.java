// UploadItemRepository.java
package com.example.imageservice.repository;

import com.example.imageservice.entity.UploadItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UploadItemRepository extends JpaRepository<UploadItem, String> {
    
    /**
     * Find items by job ID
     */
    List<UploadItem> findByUploadJob_JobId(String jobId);
    
    /**
     * Find items by status
     */
    List<UploadItem> findByStatus(UploadItem.ItemStatus status);
    
    /**
     * Find failed items for a job
     */
    List<UploadItem> findByUploadJob_JobIdAndStatus(String jobId, UploadItem.ItemStatus status);
    
    /**
     * Count items by status for a job
     */
    long countByUploadJob_JobIdAndStatus(String jobId, UploadItem.ItemStatus status);
    
    /**
     * Find items created before a certain date (for cleanup)
     */
    List<UploadItem> findByCreatedAtBefore(LocalDateTime dateTime);
    
    /**
     * Find items with CDN URLs (completed successfully)
     */
    @Query("SELECT i FROM UploadItem i WHERE i.cdnUrl IS NOT NULL AND i.cdnUrl != ''")
    List<UploadItem> findItemsWithCdnUrls();
    
    /**
     * Get error statistics
     */
    @Query("SELECT i.errorMessage, COUNT(i) FROM UploadItem i WHERE i.status = 'FAILED' AND i.errorMessage IS NOT NULL GROUP BY i.errorMessage")
    List<Object[]> getErrorStatistics();
    
    /**
     * Find large files
     */
    @Query("SELECT i FROM UploadItem i WHERE i.sizeBytes > :minSize ORDER BY i.sizeBytes DESC")
    List<UploadItem> findLargeFiles(@Param("minSize") Long minSize);
    
    // Metadata query methods
    
    /**
     * Find item by ID and user ID (security check)
     */
    @Query("SELECT i FROM UploadItem i WHERE i.itemId = :itemId AND i.uploadJob.userId = :userId")
    Optional<UploadItem> findByItemIdAndUploadJobUserId(@Param("itemId") String itemId, @Param("userId") String userId);
    
    /**
     * Find items by job ID and user ID (security check)
     */
    @Query("SELECT i FROM UploadItem i WHERE i.uploadJob.jobId = :jobId AND i.uploadJob.userId = :userId")
    List<UploadItem> findByUploadJobJobIdAndUploadJobUserId(@Param("jobId") String jobId, @Param("userId") String userId);
    
    /**
     * Find items by user ID
     */
    @Query("SELECT i FROM UploadItem i WHERE i.uploadJob.userId = :userId")
    List<UploadItem> findByUploadJobUserId(@Param("userId") String userId);
    
    /**
     * Search items by metadata criteria
     */
    @Query("SELECT i FROM UploadItem i WHERE i.uploadJob.userId = :userId " +
           "AND (:format IS NULL OR i.format = :format) " +
           "AND (:minWidth IS NULL OR i.width >= :minWidth) " +
           "AND (:maxWidth IS NULL OR i.width <= :maxWidth) " +
           "AND (:minHeight IS NULL OR i.height >= :minHeight) " +
           "AND (:maxHeight IS NULL OR i.height <= :maxHeight) " +
           "AND (:minSize IS NULL OR i.sizeBytes >= :minSize) " +
           "AND (:maxSize IS NULL OR i.sizeBytes <= :maxSize) " +
           "AND (:hasAlpha IS NULL OR i.hasAlpha = :hasAlpha) " +
           "ORDER BY i.createdAt DESC")
    Page<UploadItem> findByMetadataCriteria(
            @Param("userId") String userId,
            @Param("format") String format,
            @Param("minWidth") Integer minWidth,
            @Param("maxWidth") Integer maxWidth,
            @Param("minHeight") Integer minHeight,
            @Param("maxHeight") Integer maxHeight,
            @Param("minSize") Long minSize,
            @Param("maxSize") Long maxSize,
            @Param("hasAlpha") Boolean hasAlpha,
            Pageable pageable);
    
    /**
     * Find items by format
     */
    @Query("SELECT i FROM UploadItem i WHERE i.uploadJob.userId = :userId AND i.format = :format")
    List<UploadItem> findByUploadJobUserIdAndFormat(@Param("userId") String userId, @Param("format") String format);
    
    /**
     * Find items with alpha channel
     */
    @Query("SELECT i FROM UploadItem i WHERE i.uploadJob.userId = :userId AND i.hasAlpha = true")
    List<UploadItem> findByUploadJobUserIdAndHasAlphaTrue(@Param("userId") String userId);
    
    /**
     * Find high resolution images (high DPI)
     */
    @Query("SELECT i FROM UploadItem i WHERE i.uploadJob.userId = :userId AND (i.dpiX >= :minDpi OR i.dpiY >= :minDpi)")
    List<UploadItem> findHighResolutionImages(@Param("userId") String userId, @Param("minDpi") Integer minDpi);
    
    /**
     * Count items by job ID
     */
    long countByUploadJob_JobId(String jobId);
}