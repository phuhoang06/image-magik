package com.example.imageservice.repository;

import com.example.imageservice.entity.DesignExtractionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Design Extraction Jobs
 */
@Repository
public interface DesignExtractionJobRepository extends JpaRepository<DesignExtractionJob, Long> {
    
    /**
     * Find job by job ID
     */
    DesignExtractionJob findByJobId(String jobId);
    
    /**
     * Find jobs by status
     */
    List<DesignExtractionJob> findByStatus(String status);
    
    /**
     * Find jobs created after specific time
     */
    List<DesignExtractionJob> findByCreatedAtAfter(LocalDateTime createdAt);
    
    /**
     * Find jobs by status and created time range
     */
    @Query("SELECT j FROM DesignExtractionJob j WHERE j.status = :status AND j.createdAt BETWEEN :startTime AND :endTime")
    List<DesignExtractionJob> findByStatusAndCreatedAtBetween(
        @Param("status") String status,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime
    );
    
    /**
     * Count jobs by status
     */
    long countByStatus(String status);
    
    /**
     * Find failed jobs older than specified time
     */
    @Query("SELECT j FROM DesignExtractionJob j WHERE j.status = 'FAILED' AND j.createdAt < :cutoffTime")
    List<DesignExtractionJob> findFailedJobsOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);
}

