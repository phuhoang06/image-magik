package com.example.imageservice.repository;



import com.example.imageservice.entity.UserActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {
    
    /**
     * Find user's recent activity
     */
    List<UserActivity> findByUserIdOrderByCreatedAtDesc(String userId, org.springframework.data.domain.Pageable pageable);
    
    /**
     * Count user activities in time range
     */
    long countByUserIdAndCreatedAtBetween(String userId, LocalDateTime start, LocalDateTime end);
    
    /**
     * Find most active users
     */
    @Query("SELECT ua.userId, COUNT(ua) as activityCount FROM UserActivity ua WHERE ua.createdAt > :since GROUP BY ua.userId ORDER BY activityCount DESC")
    List<Object[]> findMostActiveUsers(@Param("since") LocalDateTime since);
}
