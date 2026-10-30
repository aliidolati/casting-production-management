package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Append-only through the application; actor identity and criterion values are independent copies. */
@Entity
@Table(name = "performance_events", indexes = @Index(name = "idx_performance_event_period", columnList = "period_id,occurred_at"))
@Getter @Setter
public class PerformanceEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "period_id", nullable = false) private PerformancePeriod period;
    private UUID criterionId;
    @Column(nullable = false, length = 40) private String action;
    @Column(nullable = false) private UUID actorId;
    @Column(nullable = false, length = 255) private String actorUsername;
    @Column(length = 255) private String actorName;
    @Column(nullable = false, name = "occurred_at") private LocalDateTime occurredAt;
    @Column(length = 2000) private String reason;
    @Column(columnDefinition = "MEDIUMTEXT") private String oldValues;
    @Column(columnDefinition = "MEDIUMTEXT") private String newValues;
    @Column(precision = 5, scale = 4) private BigDecimal oldEfficiency;
    @Column(precision = 5, scale = 4) private BigDecimal newEfficiency;
}
