package com.example.imageservice.repository;

import com.example.imageservice.entity.UploadJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UploadJobRepository extends JpaRepository<UploadJob, String> {
    
    /**
     * Find job by jobId and userId (security check)
     */
    Optional<UploadJob> findByJobIdAndUserId(String jobId, String userId);
    
    /**
     * Find job with items loaded (avoid N+1 problem)
     */
    @Query("SELECT j FROM UploadJob j LEFT JOIN FETCH j.items WHERE j.jobId = :jobId")
    Optional<UploadJob> findByIdWithItems(@Param("jobId") String jobId);
    
    /**
     * Find job by jobId and userId with items
     */
    @Query("SELECT j FROM UploadJob j LEFT JOIN FETCH j.items WHERE j.jobId = :jobId AND j.userId = :userId")
    Optional<UploadJob> findByJobIdAndUserIdWithItems(@Param("jobId") String jobId, @Param("userId") String userId);
    
    /**
     * Find user's recent jobs
     */
    List<UploadJob> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    /**
     * Count jobs by status
     */
    long countByStatus(UploadJob.JobStatus status);
    
    /**
     * Count user's jobs by status
     */
    long countByUserIdAndStatus(String userId, UploadJob.JobStatus status);
    
    /**
     * Find jobs created before a certain date (for cleanup)
     */
    List<UploadJob> findByCreatedAtBefore(LocalDateTime dateTime);
    
    /**
     * Find failed jobs for retry
     */
    @Query("SELECT j FROM UploadJob j WHERE j.status = 'FAILED' AND j.createdAt > :since")
    List<UploadJob> findFailedJobsSince(@Param("since") LocalDateTime since);
    
    /**
     * Find stuck jobs (in progress too long)
     */
    @Query("SELECT j FROM UploadJob j WHERE (j.status = 'IN_PROGRESS' OR j.status = 'SENT_TO_LAMBDA') AND j.createdAt < :stuckThreshold")
    List<UploadJob> findStuckJobs(@Param("stuckThreshold") LocalDateTime stuckThreshold);
    
    /**
     * Get user statistics
     */
    @Query("SELECT COUNT(j), SUM(j.total), SUM(j.completed), SUM(j.failed) FROM UploadJob j WHERE j.userId = :userId")
    Object[] getUserStats(@Param("userId") String userId);
    
    /**
     * Atomic increment completed count
     */
    @Query("UPDATE UploadJob j SET j.completed = j.completed + 1 WHERE j.jobId = :jobId")
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    int incrementCompletedCount(@Param("jobId") String jobId);
    
    /**
     * Atomic increment failed count
     */
    @Query("UPDATE UploadJob j SET j.failed = j.failed + 1 WHERE j.jobId = :jobId")
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    int incrementFailedCount(@Param("jobId") String jobId);
}



// U
