package com.factory.productmanager.repository;

import com.factory.productmanager.model.entity.TensileNextStage;
import com.factory.productmanager.model.entity.TensileTest;
import com.factory.productmanager.model.entity.TensileTestSample;
import com.factory.productmanager.model.entity.TensileTestStatus;
import com.factory.productmanager.service.trend.TensileTrendSampleRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TensileTestRepository extends JpaRepository<TensileTest, UUID> {

    /** Flat per-test rows keep tracking display independent of lazy test/sample collections. */
    @Query("""
        SELECT new com.factory.productmanager.repository.TensileTrackingRow(
            t.piece.id, t.id, t.testIndex, t.status, t.createdAt, t.reviewedAt,
            d.nextStage, d.durationHours, d.createdAt)
        FROM TensileTest t LEFT JOIN t.disposition d
        WHERE t.piece.id IN :pieceIds
        """)
    List<TensileTrackingRow> findTrackingContext(@Param("pieceIds") java.util.Collection<UUID> pieceIds);

    List<TensileTest> findByPieceIdOrderByTestIndexAsc(UUID pieceId);

    @Query("SELECT COALESCE(MAX(t.testIndex), 0) FROM TensileTest t WHERE t.piece.id = :pieceId")
    int findMaxTestIndexByPieceId(@Param("pieceId") UUID pieceId);

    /**
     * تست‌هایی که هنوز تعیین‌تکلیف نشده‌اند. تست‌های زیر آستانه اول می‌آیند و
     * بعد قدیمی‌ترها، چون هر دو معیار فوریت‌اند.
     */
    @Query("""
        SELECT t FROM TensileTest t
        JOIN FETCH t.piece p
        JOIN FETCH p.pieceDefinition d
        JOIN FETCH d.project
        WHERE t.status = :status
          AND (:projectId IS NULL OR d.project.id = :projectId)
        ORDER BY t.belowThreshold DESC, t.createdAt ASC
        """)
    List<TensileTest> findPendingReview(
            @Param("status") TensileTestStatus status,
            @Param("projectId") UUID projectId
    );

    long countByStatus(TensileTestStatus status);

    long countByStatusAndBelowThresholdTrue(TensileTestStatus status);

    @Query("""
        SELECT t.id FROM TensileTest t
        JOIN t.disposition disp
        WHERE disp.systemGenerated = true
          AND disp.nextStage = :nextStage
        """)
    List<UUID> findSystemAutoApprovedTestIds(@Param("nextStage") TensileNextStage nextStage);

    @Query("""
        SELECT s FROM TensileTestSample s
        JOIN FETCH s.tensileTest t
        JOIN FETCH t.piece p
        JOIN FETCH p.pieceDefinition d
        JOIN FETCH d.project proj
        WHERE (:projectId IS NULL OR proj.id = :projectId)
          AND (:pieceDefinitionId IS NULL OR d.id = :pieceDefinitionId)
          AND (:noLabelFilter = true OR s.sampleLabel IN :sampleLabels)
          AND (:from IS NULL OR t.createdAt >= :from)
          AND (:toExclusive IS NULL OR t.createdAt < :toExclusive)
        ORDER BY t.createdAt ASC, s.sampleIndex ASC
        """)
    List<TensileTestSample> findSamplesForQualityReport(
            @Param("projectId") UUID projectId,
            @Param("pieceDefinitionId") UUID pieceDefinitionId,
            @Param("noLabelFilter") boolean noLabelFilter,
            @Param("sampleLabels") java.util.Collection<String> sampleLabels,
            @Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive
    );

    /**
     * ورودی گزارش روند: فقط ستون‌های لازم (بدون لود Entity)، مرتب‌شده بر اساس زمان تست.
     * فیلتر بازه در خود SQL اعمال می‌شود و {@code pageable} سقف تعداد ردیف را تعیین می‌کند.
     */
    @Query("""
        SELECT new com.factory.productmanager.service.trend.TensileTrendSampleRow(
            s.id, t.id, t.createdAt, s.sampleLabel, s.uts, s.ys, s.el,
            proj.code, d.id, d.pieceName, p.pieceNumber)
        FROM TensileTestSample s
        JOIN s.tensileTest t
        JOIN t.piece p
        JOIN p.pieceDefinition d
        JOIN d.project proj
        WHERE (:projectId IS NULL OR proj.id = :projectId)
          AND (:pieceDefinitionId IS NULL OR d.id = :pieceDefinitionId)
          AND (:noLabelFilter = true OR s.sampleLabel IN :sampleLabels)
          AND (:from IS NULL OR t.createdAt >= :from)
          AND (:toExclusive IS NULL OR t.createdAt < :toExclusive)
          AND (s.uts IS NOT NULL OR s.ys IS NOT NULL OR s.el IS NOT NULL)
        ORDER BY t.createdAt ASC, s.sampleIndex ASC, s.id ASC
        """)
    List<TensileTrendSampleRow> findTrendRows(
            @Param("projectId") UUID projectId,
            @Param("pieceDefinitionId") UUID pieceDefinitionId,
            @Param("noLabelFilter") boolean noLabelFilter,
            @Param("sampleLabels") java.util.Collection<String> sampleLabels,
            @Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable
    );
}
