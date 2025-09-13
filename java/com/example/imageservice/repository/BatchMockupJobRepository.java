package com.example.imageservice.repository;

import com.example.imageservice.entity.BatchMockupJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BatchMockupJobRepository extends JpaRepository<BatchMockupJob, Long> {
    
    List<BatchMockupJob> findByBatchJobId(String batchJobId);
    
    List<BatchMockupJob> findByUserId(String userId);
    
    List<BatchMockupJob> findByStatus(BatchMockupJob.JobStatus status);
    
    List<BatchMockupJob> findByUserIdAndStatus(String userId, BatchMockupJob.JobStatus status);
    
    Optional<BatchMockupJob> findByBatchJobIdAndItemId(String batchJobId, String itemId);
    
    List<BatchMockupJob> findByBatchJobIdStartingWith(String prefix);
    
    boolean existsByBatchJobId(String batchJobId);
}

