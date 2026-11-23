package com.factory.productmanager.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "spectrometry_tests", indexes = {
        @Index(name = "ix_spectrometry_test_date", columnList = "test_date,id"),
        @Index(name = "ix_spectrometry_piece_date", columnList = "piece_id,test_date")
})
@Getter
@Setter
public class SpectrometryTest {
    public static final int DECIMAL_SCALE = 10;
    public static final int DECIMAL_PRECISION = 3 + DECIMAL_SCALE;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "piece_id", nullable = false, foreignKey = @ForeignKey(name = "fk_spectrometry_piece"))
    private Piece piece;

    @Column(name = "test_date", nullable = false)
    private LocalDate testDate;

    @Column(nullable = false, precision = DECIMAL_PRECISION, scale = DECIMAL_SCALE)
    private BigDecimal al;
    @Column(nullable = false, precision = DECIMAL_PRECISION, scale = DECIMAL_SCALE)
    private BigDecimal si;
    @Column(nullable = false, precision = DECIMAL_PRECISION, scale = DECIMAL_SCALE)
    private BigDecimal mg;
    @Column(nullable = false, precision = DECIMAL_PRECISION, scale = DECIMAL_SCALE)
    private BigDecimal cu;
    @Column(nullable = false, precision = DECIMAL_PRECISION, scale = DECIMAL_SCALE)
    private BigDecimal fe;
    @Column(nullable = false, precision = DECIMAL_PRECISION, scale = DECIMAL_SCALE)
    private BigDecimal mn;
    @Column(nullable = false, precision = DECIMAL_PRECISION, scale = DECIMAL_SCALE)
    private BigDecimal zn;
    @Column(nullable = false, precision = DECIMAL_PRECISION, scale = DECIMAL_SCALE)
    private BigDecimal ti;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
