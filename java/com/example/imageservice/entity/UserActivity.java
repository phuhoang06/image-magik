// UserActivity.java (Entity for tracking user activity)
package com.example.imageservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "USER_ACTIVITY")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserActivity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_activity_seq")
    @SequenceGenerator(name = "user_activity_seq", sequenceName = "USER_ACTIVITY_SEQ", allocationSize = 1)
    private Long id;
    
    @Column(name = "USER_ID", nullable = false, length = 255)
    private String userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "ACTIVITY_TYPE", nullable = false, length = 50)
    private ActivityType activityType;
    
    @Column(name = "JOB_ID", length = 36)
    private String jobId;
    
    @Column(name = "DETAILS", length = 1000)
    private String details;
    
    @Column(name = "IP_ADDRESS", length = 45)
    private String ipAddress;
    
    @Column(name = "USER_AGENT", length = 500)
    private String userAgent;
    
    @CreationTimestamp
    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
    
    public enum ActivityType {
        UPLOAD_CREATED,
        UPLOAD_COMPLETED,
        UPLOAD_FAILED,
        JOB_QUERIED,
        AUTH_SUCCESS,
        AUTH_FAILED,
        RATE_LIMIT_HIT
    }
}