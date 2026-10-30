package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "performance_periods", indexes = @Index(name = "idx_performance_user_created", columnList = "user_id,created_at"),
        uniqueConstraints = @UniqueConstraint(name = "uk_performance_open_user", columnNames = "open_user_key"))
@Getter @Setter
public class PerformancePeriod {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Version private long version;
    @Column(nullable = false, length = 160) private String title;
    /** User UUID while open; null after closure. Uniqueness also protects first-period races. */
    @Column(name = "open_user_key", length = 36) private String openUserKey;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    private LocalDateTime closedAt;
    @Column(nullable = false) private LocalDateTime updatedAt;
    /** Explicitly dirties the aggregate even when only a criterion changes. */
    @Column(nullable = false) private long revision;
}
