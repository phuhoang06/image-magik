package com.example.imageservice.repository;

import com.example.imageservice.entity.MockupJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MockupJobRepository extends JpaRepository<MockupJob, Long> {
    
    Optional<MockupJob> findByJobId(UUID jobId);
    
    List<MockupJob> findByUserId(String userId);
    
    List<MockupJob> findByStatus(MockupJob.JobStatus status);
    
    List<MockupJob> findByUserIdAndStatus(String userId, MockupJob.JobStatus status);

    @Query("SELECT m FROM MockupJob m WHERE CAST(m.jobId AS string) LIKE :prefix%")
    List<MockupJob> findByJobIdStartingWith(@Param("prefix") String prefix);
}
