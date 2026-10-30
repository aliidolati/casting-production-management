package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "performance_criteria", indexes = @Index(name = "idx_performance_criterion_period", columnList = "period_id,created_at"))
@Getter @Setter
public class PerformanceCriterion {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "period_id", nullable = false) private PerformancePeriod period;
    @Column(nullable = false, length = 200) private String title;
    @Column(length = 2000) private String description;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal maximumScore;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal weight;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal score;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}
